package com.g3cs.integration.service.impl;

import com.g3cs.integration.catalog.TprmModuleCatalog;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.EntityIdentityMapDocument;
import com.g3cs.integration.model.VendorMaster;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.service.VendorPersistService;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Test persist into vendor_master_test — no uniqueness / required-field validation.
 */
@Service
public class VendorPersistServiceImpl implements VendorPersistService {

    private static final String COLLECTION = TprmModuleCatalog.COLLECTION_VENDOR_MASTER_TEST;

    private final MongoTemplate mongoTemplate;
    private final SequenceGeneratorService sequenceGeneratorService;

    public VendorPersistServiceImpl(MongoTemplate mongoTemplate, SequenceGeneratorService sequenceGeneratorService) {
        this.mongoTemplate = mongoTemplate;
        this.sequenceGeneratorService = sequenceGeneratorService;
    }

    @Override
    public UpsertResult upsert(Map<String, Object> transformed, String integrationId,
                               String remoteResourceKey, String externalId) {
        EntityIdentityMapDocument map = findIdentity(integrationId, remoteResourceKey, externalId);
        if (map != null) {
            VendorMaster existing = mongoTemplate.findOne(
                    Query.query(Criteria.where("vendor_id").is(map.getTprmRecordId()).and("is_deleted").ne(true)),
                    VendorMaster.class,
                    COLLECTION);
            if (existing == null) {
                existing = mongoTemplate.findById(map.getTprmRecordId(), VendorMaster.class, COLLECTION);
            }
            if (existing != null) {
                applyMappedFields(existing, transformed);
                existing.setUpdatedBy(TenantContext.getUserId());
                existing.setUpdatedDt(Instant.now());
                mongoTemplate.save(existing, COLLECTION);
                return new UpsertResult(false, existing.getVendorId());
            }
        }

        VendorMaster vendor = new VendorMaster();
        vendor.setVendorId(nextVendorId());
        vendor.setTenantId(TenantContext.getTenantId());
        vendor.setIsDraft(false);
        vendor.setIsVendorOnbd(false);
        vendor.setIsEnable(true);
        vendor.setIsDeleted(false);
        vendor.setOnboardedStatus(null);
        vendor.setCreatedBy(TenantContext.getUserId());
        vendor.setCreatedDt(Instant.now());
        vendor.setUpdatedBy(TenantContext.getUserId());
        vendor.setUpdatedDt(Instant.now());
        applyMappedFields(vendor, transformed);
        mongoTemplate.save(vendor, COLLECTION);

        EntityIdentityMapDocument identity = EntityIdentityMapDocument.builder()
                .tenantId(TenantContext.getTenantId())
                .integrationId(integrationId)
                .remoteResourceKey(remoteResourceKey)
                .externalId(externalId)
                .tprmModuleKey(TprmModuleCatalog.VENDOR_MASTER_TEST)
                .tprmRecordId(vendor.getVendorId())
                .build();
        AuditSupport.onCreate(identity);
        mongoTemplate.save(identity);
        return new UpsertResult(true, vendor.getVendorId());
    }

    private void applyMappedFields(VendorMaster vendor, Map<String, Object> transformed) {
        if (transformed == null || transformed.isEmpty()) {
            return;
        }
        if (transformed.containsKey("vendorName")) {
            vendor.setVendorName(asText(transformed.get("vendorName")));
        }
        if (transformed.containsKey("companyWebsite")) {
            vendor.setCompanyWebsite(asText(transformed.get("companyWebsite")));
        }
        if (transformed.containsKey("email")) {
            vendor.setEmail(asText(transformed.get("email")));
        }
        if (transformed.containsKey("phoneNo")) {
            // Model field is Number; store numeric when possible, otherwise leave unset.
            Number phone = asNumber(transformed.get("phoneNo"));
            if (phone != null) {
                vendor.setPhoneNo(phone);
            } else {
                String phoneText = asText(transformed.get("phoneNo"));
                if (phoneText != null) {
                    vendor.setRemark(appendNote(vendor.getRemark(), "phoneNo=" + phoneText));
                }
            }
        }
        if (transformed.containsKey("address")) {
            vendor.setAddress(asText(transformed.get("address")));
        }
        if (transformed.containsKey("location")) {
            vendor.setLocation(asText(transformed.get("location")));
        }
        if (transformed.containsKey("status")) {
            vendor.setStatus(asText(transformed.get("status")));
        }
        if (transformed.containsKey("remark")) {
            vendor.setRemark(asText(transformed.get("remark")));
        }
    }

    private String appendNote(String existing, String note) {
        if (existing == null || existing.isBlank()) {
            return note;
        }
        return existing + "; " + note;
    }

    private EntityIdentityMapDocument findIdentity(String integrationId, String remoteResourceKey, String externalId) {
        Query query = Query.query(Criteria.where("tenant_id").is(TenantContext.getTenantId())
                .and("integration_id").is(integrationId)
                .and("remote_resource_key").is(remoteResourceKey)
                .and("external_id").is(externalId)
                .and("is_deleted").ne(true));
        return mongoTemplate.findOne(query, EntityIdentityMapDocument.class);
    }

    private String nextVendorId() {
        try {
            return sequenceGeneratorService.generateNextNumber("VENDOR");
        } catch (Exception e) {
            return "IHV-" + Instant.now().toEpochMilli();
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replaceAll("[^0-9.]", "");
        if (text.isBlank()) {
            return null;
        }
        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

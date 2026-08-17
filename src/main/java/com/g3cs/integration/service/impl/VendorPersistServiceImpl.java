package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.exception.RecordProcessingException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.EntityIdentityMapDocument;
import com.g3cs.integration.model.VendorMaster;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.service.VendorPersistService;
import com.g3cs.integration.tenant.TenantContext;
import com.g3cs.integration.utils.UrlSupport;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class VendorPersistServiceImpl implements VendorPersistService {

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
                    VendorMaster.class);
            if (existing == null) {
                existing = mongoTemplate.findById(map.getTprmRecordId(), VendorMaster.class);
            }
            if (existing != null) {
                applyMappedFields(existing, transformed, false);
                existing.setUpdatedBy(TenantContext.getUserId());
                existing.setUpdatedDt(Instant.now());
                mongoTemplate.save(existing);
                return new UpsertResult(false, existing.getVendorId());
            }
        }

        String vendorName = asText(transformed.get("vendorName"));
        if (vendorName == null) {
            throw new RecordProcessingException(MessageCode.VALIDATION_FAILED);
        }
        if (nameExists(vendorName, null)) {
            throw new RecordProcessingException(MessageCode.VENDOR_NAME_EXISTS);
        }
        String website = asText(transformed.get("companyWebsite"));
        if (website != null && websiteExists(website, null)) {
            throw new RecordProcessingException(MessageCode.VENDOR_WEBSITE_EXISTS);
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
        applyMappedFields(vendor, transformed, true);
        mongoTemplate.save(vendor);

        EntityIdentityMapDocument identity = EntityIdentityMapDocument.builder()
                .tenantId(TenantContext.getTenantId())
                .integrationId(integrationId)
                .remoteResourceKey(remoteResourceKey)
                .externalId(externalId)
                .tprmModuleKey("vendor_master")
                .tprmRecordId(vendor.getVendorId())
                .build();
        AuditSupport.onCreate(identity);
        mongoTemplate.save(identity);
        return new UpsertResult(true, vendor.getVendorId());
    }

    @SuppressWarnings("unchecked")
    private void applyMappedFields(VendorMaster vendor, Map<String, Object> transformed, boolean insert) {
        if (transformed.get("vendorName") != null) {
            vendor.setVendorName(asText(transformed.get("vendorName")));
        }
        if (transformed.get("companyWebsite") != null) {
            vendor.setCompanyWebsite(asText(transformed.get("companyWebsite")));
        }
        if (transformed.get("email") != null) {
            vendor.setEmail(asText(transformed.get("email")));
        }
        if (transformed.get("phoneNo") != null) {
            vendor.setPhoneNo(asNumber(transformed.get("phoneNo")));
        }
        if (transformed.get("address") != null) {
            vendor.setAddress(asText(transformed.get("address")));
        }
        if (transformed.get("location") != null) {
            vendor.setLocation(asText(transformed.get("location")));
        }
        if (transformed.get("vendorCategory") != null) {
            vendor.setVendorCategory(asText(transformed.get("vendorCategory")));
        }
        if (transformed.get("businessUnit") != null) {
            vendor.setBusinessUnit(asText(transformed.get("businessUnit")));
        }
        if (transformed.get("status") != null) {
            vendor.setStatus(asText(transformed.get("status")));
        }
        if (transformed.get("remark") != null) {
            vendor.setRemark(asText(transformed.get("remark")));
        }
        if (transformed.get("entityIds") instanceof List<?> list) {
            vendor.setEntityIds(list.stream().map(String::valueOf).toList());
        }
        if (transformed.get("regionIds") instanceof List<?> list) {
            vendor.setRegionIds(list.stream().map(String::valueOf).toList());
        }
        if (transformed.get("natureOfEngagement") instanceof List<?> list) {
            vendor.setNatureOfEngagement(list.stream().map(String::valueOf).toList());
        }
        if (transformed.get("businessFunctions") instanceof List<?> list) {
            vendor.setBusinessFunctions(list.stream().map(String::valueOf).toList());
        }
        if (!insert) {
            if (vendor.getVendorName() != null && nameExists(vendor.getVendorName(), vendor.getVendorId())) {
                throw new RecordProcessingException(MessageCode.VENDOR_NAME_EXISTS);
            }
            if (vendor.getCompanyWebsite() != null && websiteExists(vendor.getCompanyWebsite(), vendor.getVendorId())) {
                throw new RecordProcessingException(MessageCode.VENDOR_WEBSITE_EXISTS);
            }
        }
    }

    private boolean nameExists(String vendorName, String excludeVendorId) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("vendor_name").regex("^" + Pattern.quote(vendorName.trim()) + "$", "i"),
                Criteria.where("is_deleted").ne(true)
        );
        if (excludeVendorId != null) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("vendor_id").ne(excludeVendorId));
        }
        return mongoTemplate.exists(Query.query(criteria), VendorMaster.class);
    }

    private boolean websiteExists(String website, String excludeVendorId) {
        String normalized = UrlSupport.normalizeWebsite(website);
        if (normalized == null) {
            return false;
        }
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("company_website").regex(Pattern.quote(normalized), "i"),
                Criteria.where("is_deleted").ne(true)
        );
        if (excludeVendorId != null) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("vendor_id").ne(excludeVendorId));
        }
        List<VendorMaster> matches = mongoTemplate.find(Query.query(criteria), VendorMaster.class);
        return matches.stream().anyMatch(v -> normalized.equals(UrlSupport.normalizeWebsite(v.getCompanyWebsite())));
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

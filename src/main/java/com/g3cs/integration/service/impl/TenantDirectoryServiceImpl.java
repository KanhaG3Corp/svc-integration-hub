package com.g3cs.integration.service.impl;

import com.g3cs.integration.model.platform.TenantManagement;
import com.g3cs.integration.service.TenantDirectoryService;
import com.g3cs.integration.tenant.TenantContext;
import com.g3cs.integration.tenant.TenantContextBind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TenantDirectoryServiceImpl implements TenantDirectoryService {

    private final MongoTemplate mongoTemplate;
    private final String masterDatabaseName;

    public TenantDirectoryServiceImpl(MongoTemplate mongoTemplate,
                                      @Value("${spring.data.mongodb.database}") String masterDatabaseName) {
        this.mongoTemplate = mongoTemplate;
        this.masterDatabaseName = masterDatabaseName;
    }

    @Override
    public List<TenantManagement> findActiveTenantsForBackgroundJobs() {
        TenantContextBind.bindFromTenantId(TenantContext.MASTER_TENANT_ID, masterDatabaseName, masterDatabaseName);
        Criteria base = new Criteria().andOperator(
                Criteria.where("is_enabled").is(true),
                Criteria.where("is_deleted").ne(true),
                Criteria.where("is_onboarded").is(true),
                Criteria.where("is_drafted").ne(true)
        );
        Query query = Query.query(base).with(Sort.by(Sort.Direction.ASC, "tenant_id"));
        List<TenantManagement> raw = mongoTemplate.find(query, TenantManagement.class, TenantManagement.COLLECTION);
        return raw.stream()
                .filter(t -> StringUtils.hasText(t.getTenantId()) && StringUtils.hasText(t.getTenantDb()))
                .toList();
    }
}

package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.enums.ConnectorCapability;
import com.g3cs.integration.common.enums.IncrementalStrategy;
import com.g3cs.integration.common.enums.PaginationStrategy;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.ApplicationDocument;
import com.g3cs.integration.model.ConnectorDocument;
import com.g3cs.integration.model.SequenceMaster;
import com.g3cs.integration.service.CatalogSeedService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

@Service
public class CatalogSeedServiceImpl implements CatalogSeedService {

    public static final String APP_ODOO = "APP-ODOO";
    public static final String APP_CUSTOM = "APP-CUSTOM";
    public static final String CON_ODOO = "CON-ODOO-JSON2";
    public static final String CON_CUSTOM = "CON-CUSTOM-REST";

    private final MongoTemplate mongoTemplate;

    public CatalogSeedServiceImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void ensureTenantCatalog() {
        seedApplication(APP_ODOO, "Odoo", "JSON2", List.of(
                ConnectorCapability.READABLE, ConnectorCapability.WRITABLE,
                ConnectorCapability.DISCOVERABLE, ConnectorCapability.INCREMENTAL_SYNC));
        seedApplication(APP_CUSTOM, "Custom REST", "REST", List.of(
                ConnectorCapability.READABLE, ConnectorCapability.WRITABLE,
                ConnectorCapability.DISCOVERABLE));
        seedConnector(CON_ODOO, APP_ODOO, "Odoo JSON-2",
                List.of(ConnectorCapability.READABLE, ConnectorCapability.WRITABLE,
                        ConnectorCapability.DISCOVERABLE, ConnectorCapability.INCREMENTAL_SYNC),
                List.of(AuthenticationType.API_KEY, AuthenticationType.BEARER),
                PaginationStrategy.OFFSET, IncrementalStrategy.TIMESTAMP);
        seedConnector(CON_CUSTOM, APP_CUSTOM, "Custom REST",
                List.of(ConnectorCapability.READABLE, ConnectorCapability.WRITABLE, ConnectorCapability.DISCOVERABLE),
                List.of(AuthenticationType.API_KEY, AuthenticationType.BEARER, AuthenticationType.BASIC,
                        AuthenticationType.OAUTH2, AuthenticationType.CUSTOM_HEADER, AuthenticationType.NONE),
                PaginationStrategy.OFFSET, IncrementalStrategy.TIMESTAMP);
        seedSequence("CONNECTION", "CON", 6);
        seedSequence("INTEGRATION", "INT", 6);
        seedSequence("EXECUTION", "EXE", 8);
        seedSequence("AUDIT", "AUD", 8);
        ensureIndexes();
    }

    private void seedApplication(String applicationId, String name, String protocol,
                                 List<ConnectorCapability> capabilities) {
        Query query = Query.query(Criteria.where("application_id").is(applicationId).and("is_deleted").ne(true));
        if (mongoTemplate.exists(query, ApplicationDocument.class)) {
            return;
        }
        ApplicationDocument document = ApplicationDocument.builder()
                .applicationId(applicationId)
                .name(name)
                .protocol(protocol)
                .status("ACTIVE")
                .supportedCapabilities(capabilities)
                .build();
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
    }

    private void seedConnector(String connectorId, String applicationId, String name,
                               List<ConnectorCapability> capabilities,
                               List<AuthenticationType> authTypes,
                               PaginationStrategy paginationStrategy,
                               IncrementalStrategy incrementalStrategy) {
        Query query = Query.query(Criteria.where("connector_id").is(connectorId).and("is_deleted").ne(true));
        if (mongoTemplate.exists(query, ConnectorDocument.class)) {
            return;
        }
        ConnectorDocument document = ConnectorDocument.builder()
                .connectorId(connectorId)
                .applicationId(applicationId)
                .name(name)
                .status("ACTIVE")
                .capabilities(capabilities)
                .supportedAuthTypes(authTypes)
                .paginationStrategy(paginationStrategy)
                .incrementalStrategy(incrementalStrategy)
                .build();
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
    }

    private void seedSequence(String category, String prefix, int digits) {
        Query query = Query.query(Criteria.where("category").is(category));
        if (mongoTemplate.exists(query, SequenceMaster.class)) {
            return;
        }
        SequenceMaster master = SequenceMaster.builder()
                .category(category)
                .prefix(prefix)
                .suffix("")
                .numberOfDigits(digits)
                .lastNumber(0L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(1)
                .build();
        mongoTemplate.save(master);
    }

    private void ensureIndexes() {
        mongoTemplate.indexOps(com.g3cs.integration.model.ConnectionConfigurationDocument.class)
                .ensureIndex(new Index().on("tenant_id", Sort.Direction.ASC).on("name", Sort.Direction.ASC)
                        .named("ux_connection_name"));
        mongoTemplate.indexOps(com.g3cs.integration.model.IntegrationDocument.class)
                .ensureIndex(new Index().on("tenant_id", Sort.Direction.ASC).on("name", Sort.Direction.ASC)
                        .named("ux_integration_name"));
        mongoTemplate.indexOps(com.g3cs.integration.model.EntityIdentityMapDocument.class)
                .ensureIndex(new Index().on("tenant_id", Sort.Direction.ASC)
                        .on("integration_id", Sort.Direction.ASC)
                        .on("remote_resource_key", Sort.Direction.ASC)
                        .on("external_id", Sort.Direction.ASC)
                        .unique()
                        .named("ux_identity_map"));
        mongoTemplate.indexOps(com.g3cs.integration.model.IntegrationFailedRecordDocument.class)
                .ensureIndex(new Index().on("integration_id", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC)
                        .named("ix_failed_status"));
    }
}

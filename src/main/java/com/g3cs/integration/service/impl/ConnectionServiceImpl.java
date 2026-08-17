package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.dto.PageMetaResponseDto;
import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.enums.ConnectionStatus;
import com.g3cs.integration.common.enums.HealthStatus;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.dto.ConnectionUpsertRequest;
import com.g3cs.integration.model.ConnectionConfigurationDocument;
import com.g3cs.integration.model.ConnectorDocument;
import com.g3cs.integration.service.AuditService;
import com.g3cs.integration.service.CatalogSeedService;
import com.g3cs.integration.service.ConnectionService;
import com.g3cs.integration.service.CredentialService;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.spi.ConnectionTestResult;
import com.g3cs.integration.spi.ConnectorRegistry;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.ReadRequest;
import com.g3cs.integration.spi.RemoteConnector;
import com.g3cs.integration.spi.RemoteField;
import com.g3cs.integration.spi.RemoteResource;
import com.g3cs.integration.tenant.TenantContext;
import com.g3cs.integration.utils.UrlSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ConnectionServiceImpl implements ConnectionService {

    private final MongoTemplate mongoTemplate;
    private final CatalogSeedService catalogSeedService;
    private final CredentialService credentialService;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final ConnectorRegistry connectorRegistry;
    private final AuditService auditService;
    private final int sampleLimit;

    public ConnectionServiceImpl(MongoTemplate mongoTemplate,
                                 CatalogSeedService catalogSeedService,
                                 CredentialService credentialService,
                                 SequenceGeneratorService sequenceGeneratorService,
                                 ConnectorRegistry connectorRegistry,
                                 AuditService auditService,
                                 @Value("${integration.discovery.sample-limit:5}") int sampleLimit) {
        this.mongoTemplate = mongoTemplate;
        this.catalogSeedService = catalogSeedService;
        this.credentialService = credentialService;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.connectorRegistry = connectorRegistry;
        this.auditService = auditService;
        this.sampleLimit = sampleLimit;
    }

    @Override
    public ConnectionConfigurationDocument create(ConnectionUpsertRequest request) {
        catalogSeedService.ensureTenantCatalog();
        validateRequest(request, null);
        ConnectorDocument connector = requireConnector(request.getConnectorId());
        if (!connector.getSupportedAuthTypes().contains(request.getAuthenticationType())) {
            throw new BusinessException(MessageCode.AUTH_TYPE_UNSUPPORTED);
        }
        ConnectionConfigurationDocument document = ConnectionConfigurationDocument.builder()
                .connectionId(sequenceGeneratorService.generateNextNumber("CONNECTION"))
                .tenantId(TenantContext.getTenantId())
                .applicationId(request.getApplicationId())
                .connectorId(request.getConnectorId())
                .name(request.getName().trim())
                .protocol(connector.getApplicationId() != null && request.getApplicationId().contains("ODOO")
                        ? "JSON2" : "REST")
                .authenticationType(request.getAuthenticationType())
                .endpoint(ConnectionConfigurationDocument.Endpoint.builder()
                        .baseUrl(UrlSupport.trimSlash(request.getBaseUrl()))
                        .database(request.getDatabase())
                        .build())
                .credentialReference(credentialService.store(request.getCredentials()))
                .settings(request.getSettings())
                .status(ConnectionStatus.DRAFT)
                .healthStatus(HealthStatus.UNHEALTHY)
                .build();
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
        auditService.record("CONNECTION", document.getConnectionId(), null, ConnectionStatus.DRAFT.name(),
                "CONNECTION_CREATED", MessageCode.CONNECTION_CREATED.resolve(Map.of("name", document.getName())), Map.of());
        return mask(document);
    }

    @Override
    public ConnectionConfigurationDocument update(String connectionId, ConnectionUpsertRequest request) {
        ConnectionConfigurationDocument document = require(connectionId);
        validateRequest(request, connectionId);
        ConnectorDocument connector = requireConnector(request.getConnectorId());
        if (!connector.getSupportedAuthTypes().contains(request.getAuthenticationType())) {
            throw new BusinessException(MessageCode.AUTH_TYPE_UNSUPPORTED);
        }
        document.setName(request.getName().trim());
        document.setApplicationId(request.getApplicationId());
        document.setConnectorId(request.getConnectorId());
        document.setAuthenticationType(request.getAuthenticationType());
        document.setEndpoint(ConnectionConfigurationDocument.Endpoint.builder()
                .baseUrl(UrlSupport.trimSlash(request.getBaseUrl()))
                .database(request.getDatabase())
                .build());
        document.setSettings(request.getSettings());
        if (request.getCredentials() != null && !request.getCredentials().isEmpty()) {
            credentialService.replace(document.getCredentialReference(), request.getCredentials());
        }
        document.setStatus(ConnectionStatus.DRAFT);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        auditService.record("CONNECTION", document.getConnectionId(), null, document.getStatus().name(),
                "CONNECTION_UPDATED", MessageCode.CONNECTION_UPDATED.resolve(Map.of("name", document.getName())), Map.of());
        return mask(document);
    }

    @Override
    public ConnectionConfigurationDocument get(String connectionId) {
        return mask(require(connectionId));
    }

    @Override
    public PagedResponseDto<ConnectionConfigurationDocument> list(PageQueryRequestDto query) {
        catalogSeedService.ensureTenantCatalog();
        int page = query.getPage() == null ? 0 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        Criteria criteria = Criteria.where("tenant_id").is(TenantContext.getTenantId()).and("is_deleted").ne(true);
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            criteria = new Criteria().andOperator(criteria,
                    Criteria.where("name").regex(Pattern.quote(query.getSearch().trim()), "i"));
        }
        Query mongoQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.fromString(query.getSortDir() == null ? "DESC" : query.getSortDir()),
                        query.getSortBy() == null ? "updated_dt" : query.getSortBy()))
                .skip((long) page * size)
                .limit(size);
        List<ConnectionConfigurationDocument> items = mongoTemplate.find(mongoQuery, ConnectionConfigurationDocument.class)
                .stream().map(this::mask).toList();
        long total = mongoTemplate.count(Query.query(criteria), ConnectionConfigurationDocument.class);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PagedResponseDto.<ConnectionConfigurationDocument>builder()
                .items(items)
                .page(PageMetaResponseDto.builder()
                        .page(page)
                        .size(size)
                        .totalElements(total)
                        .totalPages(totalPages)
                        .hasNext(page + 1 < totalPages)
                        .hasPrevious(page > 0)
                        .build())
                .build();
    }

    @Override
    public ConnectionTestResult test(String connectionId) {
        ConnectionConfigurationDocument document = require(connectionId);
        RemoteConnector connector = connectorRegistry.require(document.getConnectorId());
        ConnectionTestResult result = connector.test(toContext(document));
        document.setLastTestedAt(Instant.now());
        document.setLastTestStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        document.setLastTestErrorCode(result.getErrorCode());
        document.setOdooVersion(result.getVersion());
        document.setHealthStatus(result.isSuccess() ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY);
        if (!result.isSuccess() && (MessageCode.CONNECTION_TEST_FAILED_AUTH.name().equals(result.getErrorCode()))) {
            document.setStatus(ConnectionStatus.AUTH_EXPIRED);
        }
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return result;
    }

    @Override
    public ConnectionConfigurationDocument activate(String connectionId) {
        ConnectionConfigurationDocument document = require(connectionId);
        ConnectionTestResult result = test(connectionId);
        document = require(connectionId);
        if (!result.isSuccess()) {
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_UNKNOWN, Map.of("name", document.getName()));
        }
        document.setStatus(ConnectionStatus.ACTIVE);
        document.setLastSuccessfulAt(Instant.now());
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return mask(document);
    }

    @Override
    public ConnectionConfigurationDocument disable(String connectionId) {
        ConnectionConfigurationDocument document = require(connectionId);
        document.setStatus(ConnectionStatus.DISABLED);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        auditService.record("CONNECTION", document.getConnectionId(), ConnectionStatus.ACTIVE.name(),
                ConnectionStatus.DISABLED.name(), "CONNECTION_DISABLED",
                MessageCode.CONNECTION_DISABLED.resolve(Map.of("name", document.getName())), Map.of());
        return mask(document);
    }

    @Override
    public void delete(String connectionId) {
        ConnectionConfigurationDocument document = require(connectionId);
        document.setIsDeleted(true);
        document.setStatus(ConnectionStatus.DISABLED);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
    }

    @Override
    public List<RemoteResource> discoverResources(String connectionId, String filter) {
        ConnectionConfigurationDocument document = requireActive(connectionId);
        return connectorRegistry.require(document.getConnectorId())
                .discoverResources(toContext(document), filter);
    }

    @Override
    public List<RemoteField> discoverFields(String connectionId, String resourceKey) {
        ConnectionConfigurationDocument document = requireActive(connectionId);
        return connectorRegistry.require(document.getConnectorId())
                .discoverFields(toContext(document), resourceKey);
    }

    @Override
    public ReadPage sample(String connectionId, String resourceKey) {
        ConnectionConfigurationDocument document = requireActive(connectionId);
        return connectorRegistry.require(document.getConnectorId())
                .searchRead(toContext(document), ReadRequest.builder()
                        .resourceKey(resourceKey)
                        .limit(sampleLimit)
                        .offset(0)
                        .fields(List.of())
                        .build());
    }

    @Override
    public ConnectionContext toContext(ConnectionConfigurationDocument connection) {
        return ConnectionContext.builder()
                .baseUrl(connection.getEndpoint() == null ? null : connection.getEndpoint().getBaseUrl())
                .database(connection.getEndpoint() == null ? null : connection.getEndpoint().getDatabase())
                .connectorId(connection.getConnectorId())
                .authenticationType(connection.getAuthenticationType() == null
                        ? AuthenticationType.NONE.name() : connection.getAuthenticationType().name())
                .secrets(credentialService.load(connection.getCredentialReference()))
                .settings(connection.getSettings())
                .build();
    }

    private void validateRequest(ConnectionUpsertRequest request, String excludeConnectionId) {
        UrlSupport.requireHttpsBaseUrl(request.getBaseUrl());
        if (request.getAuthenticationType() == AuthenticationType.OAUTH2
                && (request.getCredentials() == null || isBlank(request.getCredentials().get("tokenUrl")))) {
            throw new BusinessException(MessageCode.VALIDATION_FAILED);
        }
        Query dup = Query.query(Criteria.where("tenant_id").is(TenantContext.getTenantId())
                .and("name").regex("^" + Pattern.quote(request.getName().trim()) + "$", "i")
                .and("is_deleted").ne(true));
        if (excludeConnectionId != null) {
            dup.addCriteria(Criteria.where("connection_id").ne(excludeConnectionId));
        }
        if (mongoTemplate.exists(dup, ConnectionConfigurationDocument.class)) {
            throw new BusinessException(MessageCode.CONNECTION_DUPLICATE_NAME, Map.of("name", request.getName()));
        }
    }

    private ConnectorDocument requireConnector(String connectorId) {
        ConnectorDocument connector = mongoTemplate.findOne(
                Query.query(Criteria.where("connector_id").is(connectorId).and("is_deleted").ne(true)),
                ConnectorDocument.class);
        if (connector == null) {
            throw new BusinessException(MessageCode.RESOURCE_NOT_FOUND, Map.of("resource", "connector"), 404);
        }
        return connector;
    }

    private ConnectionConfigurationDocument require(String connectionId) {
        ConnectionConfigurationDocument document = mongoTemplate.findOne(
                Query.query(Criteria.where("connection_id").is(connectionId).and("is_deleted").ne(true)),
                ConnectionConfigurationDocument.class);
        if (document == null) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_FOUND, Map.of(), 404);
        }
        return document;
    }

    private ConnectionConfigurationDocument requireActive(String connectionId) {
        ConnectionConfigurationDocument document = require(connectionId);
        if (document.getStatus() != ConnectionStatus.ACTIVE) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_ACTIVE);
        }
        return document;
    }

    private ConnectionConfigurationDocument mask(ConnectionConfigurationDocument document) {
        document.setCredentialReference(document.getCredentialReference() == null ? null : "***");
        return document;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

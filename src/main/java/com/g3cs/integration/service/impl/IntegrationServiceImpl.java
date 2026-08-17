package com.g3cs.integration.service.impl;

import com.g3cs.integration.catalog.TprmModuleCatalog;
import com.g3cs.integration.common.dto.PageMetaResponseDto;
import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.common.enums.ConflictPolicy;
import com.g3cs.integration.common.enums.ConnectionStatus;
import com.g3cs.integration.common.enums.FailedRecordStatus;
import com.g3cs.integration.common.enums.IntegrationOperation;
import com.g3cs.integration.common.enums.IntegrationStatus;
import com.g3cs.integration.common.enums.SyncDirection;
import com.g3cs.integration.common.enums.SyncMode;
import com.g3cs.integration.common.enums.TransformMode;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.dto.IntegrationUpsertRequest;
import com.g3cs.integration.dto.RetryFailedRecordsRequest;
import com.g3cs.integration.model.ConnectionConfigurationDocument;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.IntegrationExecutionDocument;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;
import com.g3cs.integration.service.AuditService;
import com.g3cs.integration.service.CatalogSeedService;
import com.g3cs.integration.service.ConnectionService;
import com.g3cs.integration.service.FailedRecordService;
import com.g3cs.integration.service.IntegrationService;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.service.SyncEngineService;
import com.g3cs.integration.service.TransformEngine;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class IntegrationServiceImpl implements IntegrationService {

    private final MongoTemplate mongoTemplate;
    private final CatalogSeedService catalogSeedService;
    private final ConnectionService connectionService;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final SyncEngineService syncEngineService;
    private final FailedRecordService failedRecordService;
    private final TransformEngine transformEngine;
    private final TprmModuleCatalog tprmModuleCatalog;
    private final AuditService auditService;

    public IntegrationServiceImpl(MongoTemplate mongoTemplate,
                                  CatalogSeedService catalogSeedService,
                                  ConnectionService connectionService,
                                  SequenceGeneratorService sequenceGeneratorService,
                                  SyncEngineService syncEngineService,
                                  FailedRecordService failedRecordService,
                                  TransformEngine transformEngine,
                                  TprmModuleCatalog tprmModuleCatalog,
                                  AuditService auditService) {
        this.mongoTemplate = mongoTemplate;
        this.catalogSeedService = catalogSeedService;
        this.connectionService = connectionService;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.syncEngineService = syncEngineService;
        this.failedRecordService = failedRecordService;
        this.transformEngine = transformEngine;
        this.tprmModuleCatalog = tprmModuleCatalog;
        this.auditService = auditService;
    }

    @Override
    public IntegrationDocument create(IntegrationUpsertRequest request) {
        catalogSeedService.ensureTenantCatalog();
        requireConnectionActive(request.getConnectionId());
        assertUniqueName(request.getName(), null);
        IntegrationDocument document = IntegrationDocument.builder()
                .integrationId(sequenceGeneratorService.generateNextNumber("INTEGRATION"))
                .tenantId(TenantContext.getTenantId())
                .tenantDb(TenantContext.getTenantDB())
                .name(request.getName().trim())
                .description(request.getDescription())
                .connectionId(request.getConnectionId())
                .applicationId(null)
                .tprmModuleKey(request.getTprmModuleKey() == null ? TprmModuleCatalog.VENDOR_MASTER : request.getTprmModuleKey())
                .remoteResourceKey(request.getRemoteResourceKey())
                .operation(request.getOperation() == null ? IntegrationOperation.UPSERT : request.getOperation())
                .direction(request.getDirection() == null ? SyncDirection.INBOUND : request.getDirection())
                .conflictPolicy(request.getConflictPolicy() == null ? ConflictPolicy.REMOTE_WINS : request.getConflictPolicy())
                .syncMode(request.getSyncMode() == null ? SyncMode.MANUAL : request.getSyncMode())
                .paginationEnabled(request.getPaginationEnabled() == null || request.getPaginationEnabled())
                .batchSize(request.getBatchSize())
                .periodicIntervalMinutes(request.getPeriodicIntervalMinutes())
                .scheduleCron(request.getScheduleCron())
                .scheduleTimezone(request.getScheduleTimezone())
                .incrementalEnabled(request.getIncrementalEnabled() == null || request.getIncrementalEnabled())
                .mapping(request.getMapping())
                .mappingVersion(1)
                .remoteDomain(request.getRemoteDomain())
                .status(IntegrationStatus.DRAFT)
                .build();
        ConnectionConfigurationDocument connection = loadConnection(request.getConnectionId());
        document.setApplicationId(connection.getApplicationId());
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
        auditService.record("INTEGRATION", document.getIntegrationId(), null, IntegrationStatus.DRAFT.name(),
                "INTEGRATION_CREATED", MessageCode.INTEGRATION_CREATED.resolve(Map.of("name", document.getName())), Map.of());
        return document;
    }

    @Override
    public IntegrationDocument update(String integrationId, IntegrationUpsertRequest request) {
        IntegrationDocument document = require(integrationId);
        assertUniqueName(request.getName(), integrationId);
        requireConnectionActive(request.getConnectionId());
        document.setName(request.getName().trim());
        document.setDescription(request.getDescription());
        document.setConnectionId(request.getConnectionId());
        document.setTprmModuleKey(request.getTprmModuleKey() == null ? document.getTprmModuleKey() : request.getTprmModuleKey());
        document.setRemoteResourceKey(request.getRemoteResourceKey());
        if (request.getOperation() != null) {
            document.setOperation(request.getOperation());
        }
        if (request.getDirection() != null) {
            document.setDirection(request.getDirection());
        }
        if (request.getConflictPolicy() != null) {
            document.setConflictPolicy(request.getConflictPolicy());
        }
        if (request.getSyncMode() != null) {
            document.setSyncMode(request.getSyncMode());
        }
        if (request.getPaginationEnabled() != null) {
            document.setPaginationEnabled(request.getPaginationEnabled());
        }
        document.setBatchSize(request.getBatchSize());
        document.setPeriodicIntervalMinutes(request.getPeriodicIntervalMinutes());
        document.setScheduleCron(request.getScheduleCron());
        document.setScheduleTimezone(request.getScheduleTimezone());
        if (request.getIncrementalEnabled() != null) {
            document.setIncrementalEnabled(request.getIncrementalEnabled());
        }
        if (request.getMapping() != null) {
            document.setMapping(request.getMapping());
            document.setMappingVersion(document.getMappingVersion() == null ? 1 : document.getMappingVersion() + 1);
        }
        document.setRemoteDomain(request.getRemoteDomain());
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return document;
    }

    @Override
    public IntegrationDocument get(String integrationId) {
        return require(integrationId);
    }

    @Override
    public PagedResponseDto<IntegrationDocument> list(PageQueryRequestDto query) {
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
        List<IntegrationDocument> items = mongoTemplate.find(mongoQuery, IntegrationDocument.class);
        long total = mongoTemplate.count(Query.query(criteria), IntegrationDocument.class);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PagedResponseDto.<IntegrationDocument>builder()
                .items(items)
                .page(pageMeta(page, size, total, totalPages))
                .build();
    }

    @Override
    public IntegrationDocument saveMapping(String integrationId, List<IntegrationDocument.FieldMapping> mapping) {
        IntegrationDocument document = require(integrationId);
        document.setMapping(mapping);
        document.setMappingVersion(document.getMappingVersion() == null ? 1 : document.getMappingVersion() + 1);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return document;
    }

    @Override
    public IntegrationDocument activate(String integrationId) {
        IntegrationDocument document = require(integrationId);
        requireConnectionActive(document.getConnectionId());
        assertMappingComplete(document);
        document.setStatus(IntegrationStatus.ACTIVE);
        if (document.getPeriodicIntervalMinutes() != null && document.getPeriodicIntervalMinutes() > 0) {
            document.setNextRunAt(Instant.now().plus(Duration.ofMinutes(document.getPeriodicIntervalMinutes())));
        }
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return document;
    }

    @Override
    public IntegrationDocument deactivate(String integrationId) {
        IntegrationDocument document = require(integrationId);
        document.setStatus(IntegrationStatus.PAUSED);
        document.setNextRunAt(null);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
        return document;
    }

    @Override
    public Map<String, Object> preview(String integrationId) {
        IntegrationDocument document = require(integrationId);
        requireConnectionActive(document.getConnectionId());
        ReadPage sample = connectionService.sample(document.getConnectionId(), document.getRemoteResourceKey());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (sample.getRecords() != null) {
            for (Map<String, Object> record : sample.getRecords()) {
                rows.add(transformEngine.transform(record, document.getMapping()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("remote", sample.getRecords());
        result.put("transformed", rows);
        return result;
    }

    @Override
    public IntegrationExecutionDocument sync(String integrationId) {
        IntegrationDocument document = require(integrationId);
        requireConnectionActive(document.getConnectionId());
        if (document.getStatus() != IntegrationStatus.ACTIVE) {
            throw new BusinessException(MessageCode.INTEGRATION_ACTIVATE_FAILED);
        }
        auditService.record("INTEGRATION", document.getIntegrationId(), document.getStatus().name(),
                document.getStatus().name(), "INTEGRATION_SYNC_STARTED",
                MessageCode.INTEGRATION_SYNC_STARTED.resolve(Map.of("name", document.getName())), Map.of());
        return syncEngineService.run(document, "MANUAL");
    }

    @Override
    public PagedResponseDto<IntegrationExecutionDocument> executions(String integrationId, PageQueryRequestDto query) {
        require(integrationId);
        int page = query.getPage() == null ? 0 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        Criteria criteria = Criteria.where("integration_id").is(integrationId).and("is_deleted").ne(true);
        Query mongoQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "started_at"))
                .skip((long) page * size)
                .limit(size);
        List<IntegrationExecutionDocument> items = mongoTemplate.find(mongoQuery, IntegrationExecutionDocument.class);
        long total = mongoTemplate.count(Query.query(criteria), IntegrationExecutionDocument.class);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PagedResponseDto.<IntegrationExecutionDocument>builder()
                .items(items)
                .page(pageMeta(page, size, total, totalPages))
                .build();
    }

    @Override
    public PagedResponseDto<IntegrationFailedRecordDocument> failedRecords(String integrationId, String executionId,
                                                                          PageQueryRequestDto query) {
        require(integrationId);
        return failedRecordService.list(integrationId, executionId, query);
    }

    @Override
    public void retrySelected(String integrationId, RetryFailedRecordsRequest request) {
        IntegrationDocument document = require(integrationId);
        List<IntegrationFailedRecordDocument> records = new ArrayList<>();
        if (request.getFailedRecordIds() != null) {
            for (String id : request.getFailedRecordIds()) {
                IntegrationFailedRecordDocument record = failedRecordService.require(id);
                if (!integrationId.equals(record.getIntegrationId())) {
                    throw new BusinessException(MessageCode.FAILED_RECORD_NOT_FOUND, Map.of(), 404);
                }
                records.add(record);
            }
        }
        syncEngineService.retryRecords(document, records);
    }

    @Override
    public void retryAll(String integrationId, String executionId) {
        IntegrationDocument document = require(integrationId);
        Criteria criteria = Criteria.where("integration_id").is(integrationId)
                .and("status").is(FailedRecordStatus.FAILED)
                .and("is_deleted").ne(true);
        if (executionId != null && !executionId.isBlank()) {
            criteria = criteria.and("execution_id").is(executionId);
        }
        List<IntegrationFailedRecordDocument> records = mongoTemplate.find(Query.query(criteria),
                IntegrationFailedRecordDocument.class);
        syncEngineService.retryRecords(document, records);
    }

    private void assertMappingComplete(IntegrationDocument document) {
        if (document.getRemoteResourceKey() == null || document.getRemoteResourceKey().isBlank()) {
            throw new BusinessException(MessageCode.INTEGRATION_ACTIVATE_FAILED);
        }
        List<IntegrationDocument.FieldMapping> mapping = document.getMapping() == null
                ? List.of() : document.getMapping();
        boolean vendorName = mapping.stream().anyMatch(m -> m.isIncluded()
                && "vendorName".equals(m.getTprmField()) && m.getRemoteField() != null && !m.getRemoteField().isBlank());
        boolean entity = mapping.stream().anyMatch(m -> m.isIncluded()
                && m.getTransformMode() == TransformMode.RESOLVE_ENTITY);
        if (!vendorName || !entity) {
            throw new BusinessException(MessageCode.INTEGRATION_ACTIVATE_FAILED);
        }
    }

    private void assertUniqueName(String name, String excludeId) {
        Query query = Query.query(Criteria.where("tenant_id").is(TenantContext.getTenantId())
                .and("name").regex("^" + Pattern.quote(name.trim()) + "$", "i")
                .and("is_deleted").ne(true));
        if (excludeId != null) {
            query.addCriteria(Criteria.where("integration_id").ne(excludeId));
        }
        if (mongoTemplate.exists(query, IntegrationDocument.class)) {
            throw new BusinessException(MessageCode.VALIDATION_FAILED);
        }
    }

    private void requireConnectionActive(String connectionId) {
        ConnectionConfigurationDocument connection = loadConnection(connectionId);
        if (connection.getStatus() != ConnectionStatus.ACTIVE) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_ACTIVE);
        }
    }

    private ConnectionConfigurationDocument loadConnection(String connectionId) {
        ConnectionConfigurationDocument connection = mongoTemplate.findOne(
                Query.query(Criteria.where("connection_id").is(connectionId).and("is_deleted").ne(true)),
                ConnectionConfigurationDocument.class);
        if (connection == null) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_FOUND, Map.of(), 404);
        }
        return connection;
    }

    private IntegrationDocument require(String integrationId) {
        IntegrationDocument document = mongoTemplate.findOne(
                Query.query(Criteria.where("integration_id").is(integrationId).and("is_deleted").ne(true)),
                IntegrationDocument.class);
        if (document == null) {
            throw new BusinessException(MessageCode.INTEGRATION_NOT_FOUND, Map.of(), 404);
        }
        return document;
    }

    private PageMetaResponseDto pageMeta(int page, int size, long total, int totalPages) {
        return PageMetaResponseDto.builder()
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .hasNext(page + 1 < totalPages)
                .hasPrevious(page > 0)
                .build();
    }
}

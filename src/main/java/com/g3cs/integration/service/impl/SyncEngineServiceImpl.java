package com.g3cs.integration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g3cs.integration.catalog.TprmModuleCatalog;
import com.g3cs.integration.common.enums.ConnectionStatus;
import com.g3cs.integration.common.enums.ExecutionStatus;
import com.g3cs.integration.common.enums.FailedRecordStatus;
import com.g3cs.integration.common.enums.IntegrationStatus;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.exception.RecordProcessingException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.ConnectionConfigurationDocument;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.IntegrationExecutionDocument;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;
import com.g3cs.integration.service.ConnectionService;
import com.g3cs.integration.service.FailedRecordService;
import com.g3cs.integration.service.MasterValueResolver;
import com.g3cs.integration.service.RedisService;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.service.SyncEngineService;
import com.g3cs.integration.service.TransformEngine;
import com.g3cs.integration.service.VendorPersistService;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.spi.ConnectorRegistry;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.ReadRequest;
import com.g3cs.integration.spi.RemoteConnector;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SyncEngineServiceImpl implements SyncEngineService {

    private static final DateTimeFormatter ODOO_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<Object>> DOMAIN_TYPE = new TypeReference<>() {};

    private final MongoTemplate mongoTemplate;
    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final TransformEngine transformEngine;
    private final MasterValueResolver masterValueResolver;
    private final VendorPersistService vendorPersistService;
    private final FailedRecordService failedRecordService;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final RedisService redisService;
    private final TprmModuleCatalog tprmModuleCatalog;
    private final ObjectMapper objectMapper;
    private final int defaultBatchSize;
    private final int maxBatchSize;
    private final int maxRowsWithoutPagination;
    private final long lockTtlSeconds;

    public SyncEngineServiceImpl(MongoTemplate mongoTemplate,
                                 ConnectionService connectionService,
                                 ConnectorRegistry connectorRegistry,
                                 TransformEngine transformEngine,
                                 MasterValueResolver masterValueResolver,
                                 VendorPersistService vendorPersistService,
                                 FailedRecordService failedRecordService,
                                 SequenceGeneratorService sequenceGeneratorService,
                                 RedisService redisService,
                                 TprmModuleCatalog tprmModuleCatalog,
                                 ObjectMapper objectMapper,
                                 @Value("${integration.sync.default-batch-size:100}") int defaultBatchSize,
                                 @Value("${integration.sync.max-batch-size:500}") int maxBatchSize,
                                 @Value("${integration.sync.max-rows-without-pagination:2000}") int maxRowsWithoutPagination,
                                 @Value("${integration.sync.lock-ttl-seconds:900}") long lockTtlSeconds) {
        this.mongoTemplate = mongoTemplate;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.transformEngine = transformEngine;
        this.masterValueResolver = masterValueResolver;
        this.vendorPersistService = vendorPersistService;
        this.failedRecordService = failedRecordService;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.redisService = redisService;
        this.tprmModuleCatalog = tprmModuleCatalog;
        this.objectMapper = objectMapper;
        this.defaultBatchSize = defaultBatchSize;
        this.maxBatchSize = maxBatchSize;
        this.maxRowsWithoutPagination = maxRowsWithoutPagination;
        this.lockTtlSeconds = lockTtlSeconds;
    }

    @Override
    public IntegrationExecutionDocument run(IntegrationDocument integration, String triggeredBy) {
        if (integration.getStatus() != IntegrationStatus.ACTIVE) {
            throw new BusinessException(MessageCode.INTEGRATION_ACTIVATE_FAILED);
        }
        ConnectionConfigurationDocument connection = connectionService.get(integration.getConnectionId());
        // get() masks credentials; load unmasked by rebuilding context via require path
        connection = mongoTemplate.findOne(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("connection_id")
                                .is(integration.getConnectionId()).and("is_deleted").ne(true)),
                ConnectionConfigurationDocument.class);
        if (connection == null || connection.getStatus() != ConnectionStatus.ACTIVE) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_ACTIVE);
        }
        String lockKey = "integration:lock:" + TenantContext.getTenantId() + ":" + integration.getIntegrationId();
        if (!redisService.tryLock(lockKey, Duration.ofSeconds(lockTtlSeconds))) {
            throw new BusinessException(MessageCode.INTEGRATION_ALREADY_RUNNING);
        }
        IntegrationExecutionDocument execution = startExecution(integration, triggeredBy);
        try {
            ConnectionContext context = connectionService.toContext(connection);
            RemoteConnector connector = connectorRegistry.require(connection.getConnectorId());
            boolean paginationOn = Boolean.TRUE.equals(integration.getPaginationEnabled());
            int batchSize = resolveBatchSize(integration.getBatchSize());
            int offset = integration.getLastOffset() == null ? 0 : integration.getLastOffset();
            if (Boolean.TRUE.equals(integration.getIncrementalEnabled()) && integration.getLastWriteDate() != null) {
                offset = 0;
            }
            List<String> fields = mappedRemoteFields(integration);
            List<Object> domain = buildDomain(integration);
            boolean more = true;
            boolean firstFetch = true;
            while (more) {
                int limit = paginationOn ? batchSize : maxRowsWithoutPagination;
                ReadPage page = connector.searchRead(context, ReadRequest.builder()
                        .resourceKey(integration.getRemoteResourceKey())
                        .fields(fields)
                        .domain(domain)
                        .offset(offset)
                        .limit(limit)
                        .order("write_date asc, id asc")
                        .build());
                if (!paginationOn && firstFetch && page.isHasMore()) {
                    failExecution(execution, MessageCode.PAGINATION_REQUIRED);
                    throw new BusinessException(MessageCode.PAGINATION_REQUIRED);
                }
                firstFetch = false;
                Instant maxWriteDate = integration.getLastWriteDate();
                String lastExternalId = integration.getLastExternalId();
                for (Map<String, Object> record : page.getRecords()) {
                    execution.setRecordsRead(n(execution.getRecordsRead()) + 1);
                    try {
                        boolean inserted = processRemoteRecord(integration, execution.getExecutionId(), record);
                        if (inserted) {
                            execution.setRecordsInserted(n(execution.getRecordsInserted()) + 1);
                        } else {
                            execution.setRecordsUpdated(n(execution.getRecordsUpdated()) + 1);
                        }
                    } catch (RecordProcessingException e) {
                        execution.setRecordsFailed(n(execution.getRecordsFailed()) + 1);
                        failedRecordService.recordFailure(integration.getIntegrationId(), execution.getExecutionId(),
                                integration.getTprmModuleKey(), externalId(record), record,
                                e.getCode().name(), e.getCode().resolve(e.getArgs()));
                    } catch (Exception e) {
                        execution.setRecordsFailed(n(execution.getRecordsFailed()) + 1);
                        failedRecordService.recordFailure(integration.getIntegrationId(), execution.getExecutionId(),
                                integration.getTprmModuleKey(), externalId(record), record,
                                MessageCode.INTERNAL_ERROR.name(), MessageCode.INTERNAL_ERROR.template());
                    }
                    Instant writeDate = parseWriteDate(record.get("write_date"));
                    if (writeDate != null && (maxWriteDate == null || writeDate.isAfter(maxWriteDate))) {
                        maxWriteDate = writeDate;
                    }
                    lastExternalId = externalId(record);
                }
                offset = page.getNextOffset() == null ? offset + page.getRecords().size() : page.getNextOffset();
                more = paginationOn && page.isHasMore();
                integration.setLastOffset(offset);
                integration.setLastWriteDate(maxWriteDate);
                integration.setLastExternalId(lastExternalId);
                integration.setLastSuccessfulAt(Instant.now());
                AuditSupport.onUpdate(integration);
                mongoTemplate.save(integration);
                mongoTemplate.save(execution);
                if (!paginationOn) {
                    more = false;
                }
            }
            finishExecution(execution, integration);
            return execution;
        } catch (BusinessException e) {
            failExecution(execution, e.getCode());
            throw e;
        } catch (Exception e) {
            failExecution(execution, MessageCode.INTERNAL_ERROR);
            throw e;
        } finally {
            redisService.unlock(lockKey);
        }
    }

    @Override
    public boolean processRemoteRecord(IntegrationDocument integration, String executionId, Map<String, Object> remoteRecord) {
        Map<String, Object> transformed = transformEngine.transform(remoteRecord, integration.getMapping());
        assertRequiredPresent(transformed, integration);
        masterValueResolver.applyResolves(transformed, integration.getMapping());
        VendorPersistService.UpsertResult result = vendorPersistService.upsert(
                transformed, integration.getIntegrationId(), integration.getRemoteResourceKey(), externalId(remoteRecord));
        return result.inserted();
    }

    @Override
    public void retryRecords(IntegrationDocument integration, List<IntegrationFailedRecordDocument> records) {
        failedRecordService.markRetrying(records);
        for (IntegrationFailedRecordDocument record : records) {
            try {
                processRemoteRecord(integration, record.getExecutionId(), record.getPayloadSnapshot());
                failedRecordService.markResolved(record);
            } catch (RecordProcessingException e) {
                record.setStatus(FailedRecordStatus.FAILED);
                record.setErrorCode(e.getCode().name());
                record.setErrorMessage(e.getCode().resolve(e.getArgs()));
                AuditSupport.onUpdate(record);
                mongoTemplate.save(record);
            } catch (Exception e) {
                record.setStatus(FailedRecordStatus.FAILED);
                record.setErrorCode(MessageCode.INTERNAL_ERROR.name());
                record.setErrorMessage(MessageCode.INTERNAL_ERROR.template());
                AuditSupport.onUpdate(record);
                mongoTemplate.save(record);
            }
        }
    }

    private void assertRequiredPresent(Map<String, Object> transformed, IntegrationDocument integration) {
        for (String key : tprmModuleCatalog.requiredFieldKeys(integration.getTprmModuleKey())) {
            Object value = transformed.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new RecordProcessingException(MessageCode.VALIDATION_FAILED);
            }
        }
    }

    private IntegrationExecutionDocument startExecution(IntegrationDocument integration, String triggeredBy) {
        IntegrationExecutionDocument execution = IntegrationExecutionDocument.builder()
                .executionId(sequenceGeneratorService.generateNextNumber("EXECUTION"))
                .tenantId(TenantContext.getTenantId())
                .integrationId(integration.getIntegrationId())
                .status(ExecutionStatus.RUNNING)
                .triggeredBy(triggeredBy)
                .startedAt(Instant.now())
                .recordsRead(0)
                .recordsInserted(0)
                .recordsUpdated(0)
                .recordsFailed(0)
                .build();
        AuditSupport.onCreate(execution);
        mongoTemplate.save(execution);
        return execution;
    }

    private void finishExecution(IntegrationExecutionDocument execution, IntegrationDocument integration) {
        execution.setFinishedAt(Instant.now());
        if (n(execution.getRecordsFailed()) == 0) {
            execution.setStatus(ExecutionStatus.SUCCESS);
        } else if (n(execution.getRecordsInserted()) + n(execution.getRecordsUpdated()) > 0) {
            execution.setStatus(ExecutionStatus.PARTIAL);
        } else {
            execution.setStatus(ExecutionStatus.FAILED);
        }
        AuditSupport.onUpdate(execution);
        mongoTemplate.save(execution);
        auditNextRun(integration);
    }

    private void failExecution(IntegrationExecutionDocument execution, MessageCode code) {
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFinishedAt(Instant.now());
        execution.setErrorCode(code.name());
        execution.setErrorMessage(code.template());
        AuditSupport.onUpdate(execution);
        mongoTemplate.save(execution);
    }

    private void auditNextRun(IntegrationDocument integration) {
        Instant next = null;
        if (integration.getPeriodicIntervalMinutes() != null && integration.getPeriodicIntervalMinutes() > 0) {
            next = Instant.now().plus(Duration.ofMinutes(integration.getPeriodicIntervalMinutes()));
        }
        integration.setNextRunAt(next);
        mongoTemplate.save(integration);
    }

    private List<String> mappedRemoteFields(IntegrationDocument integration) {
        List<String> fields = new ArrayList<>();
        fields.add("id");
        fields.add("write_date");
        if (integration.getMapping() != null) {
            for (IntegrationDocument.FieldMapping mapping : integration.getMapping()) {
                if (mapping.isIncluded() && mapping.getRemoteField() != null && !mapping.getRemoteField().isBlank()) {
                    fields.add(mapping.getRemoteField());
                }
            }
        }
        return fields.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> buildDomain(IntegrationDocument integration) {
        List<Object> domain = new ArrayList<>();
        if (integration.getRemoteDomain() != null && !integration.getRemoteDomain().isBlank()) {
            try {
                domain.addAll(objectMapper.readValue(integration.getRemoteDomain(), DOMAIN_TYPE));
            } catch (Exception ignored) {
                // ignore malformed saved domain
            }
        }
        if (Boolean.TRUE.equals(integration.getIncrementalEnabled()) && integration.getLastWriteDate() != null) {
            String stamp = ODOO_DT.format(integration.getLastWriteDate().atOffset(ZoneOffset.UTC));
            domain.add(List.of("write_date", ">", stamp));
        }
        return domain;
    }

    private int resolveBatchSize(Integer requested) {
        int size = requested == null ? defaultBatchSize : requested;
        return Math.max(1, Math.min(size, maxBatchSize));
    }

    private String externalId(Map<String, Object> record) {
        if (record == null || record.get("id") == null) {
            return "unknown";
        }
        return String.valueOf(record.get("id"));
    }

    private Instant parseWriteDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        try {
            String text = String.valueOf(value).replace('T', ' ');
            if (text.length() >= 19) {
                return LocalDateTime.parse(text.substring(0, 19), ODOO_DT).toInstant(ZoneOffset.UTC);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private int n(Integer value) {
        return value == null ? 0 : value;
    }
}

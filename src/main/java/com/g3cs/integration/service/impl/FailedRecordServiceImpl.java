package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.dto.PageMetaResponseDto;
import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.common.enums.FailedRecordStatus;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;
import com.g3cs.integration.service.FailedRecordService;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FailedRecordServiceImpl implements FailedRecordService {

    private static final int MAX_STRING = 4000;
    private static final int MAX_KEYS = 50;

    private final MongoTemplate mongoTemplate;
    private final SequenceGeneratorService sequenceGeneratorService;

    public FailedRecordServiceImpl(MongoTemplate mongoTemplate, SequenceGeneratorService sequenceGeneratorService) {
        this.mongoTemplate = mongoTemplate;
        this.sequenceGeneratorService = sequenceGeneratorService;
    }

    @Override
    public void recordFailure(String integrationId, String executionId, String tprmModuleKey, String externalId,
                              Map<String, Object> payload, String errorCode, String errorMessage) {
        IntegrationFailedRecordDocument document = IntegrationFailedRecordDocument.builder()
                .failedRecordId(sequenceGeneratorService.generateNextNumber("EXECUTION"))
                .tenantId(TenantContext.getTenantId())
                .integrationId(integrationId)
                .executionId(executionId)
                .externalId(externalId)
                .tprmModuleKey(tprmModuleKey)
                .payloadSnapshot(bound(payload))
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .status(FailedRecordStatus.FAILED)
                .retryCount(0)
                .build();
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
    }

    @Override
    public PagedResponseDto<IntegrationFailedRecordDocument> list(String integrationId, String executionId,
                                                                  PageQueryRequestDto query) {
        int page = query.getPage() == null ? 0 : query.getPage();
        int size = query.getSize() == null ? 20 : query.getSize();
        Criteria criteria = Criteria.where("integration_id").is(integrationId).and("is_deleted").ne(true);
        if (executionId != null && !executionId.isBlank()) {
            criteria = criteria.and("execution_id").is(executionId);
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(
                    Criteria.where("external_id").regex(query.getSearch().trim(), "i"),
                    Criteria.where("error_code").regex(query.getSearch().trim(), "i"),
                    Criteria.where("error_message").regex(query.getSearch().trim(), "i")
            ));
        }
        Query mongoQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "created_dt"))
                .skip((long) page * size)
                .limit(size);
        List<IntegrationFailedRecordDocument> items = mongoTemplate.find(mongoQuery, IntegrationFailedRecordDocument.class);
        long total = mongoTemplate.count(Query.query(criteria), IntegrationFailedRecordDocument.class);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PagedResponseDto.<IntegrationFailedRecordDocument>builder()
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
    public IntegrationFailedRecordDocument require(String failedRecordId) {
        IntegrationFailedRecordDocument document = mongoTemplate.findOne(
                Query.query(Criteria.where("failed_record_id").is(failedRecordId).and("is_deleted").ne(true)),
                IntegrationFailedRecordDocument.class);
        if (document == null) {
            throw new BusinessException(MessageCode.FAILED_RECORD_NOT_FOUND, Map.of(), 404);
        }
        return document;
    }

    @Override
    public void markRetrying(List<IntegrationFailedRecordDocument> records) {
        for (IntegrationFailedRecordDocument record : records) {
            record.setStatus(FailedRecordStatus.RETRYING);
            record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
            record.setLastRetryAt(Instant.now());
            AuditSupport.onUpdate(record);
            mongoTemplate.save(record);
        }
    }

    @Override
    public void markResolved(IntegrationFailedRecordDocument record) {
        record.setStatus(FailedRecordStatus.RESOLVED);
        AuditSupport.onUpdate(record);
        mongoTemplate.save(record);
    }

    private Map<String, Object> bound(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (i++ >= MAX_KEYS) {
                break;
            }
            Object value = entry.getValue();
            if (value instanceof String text && text.length() > MAX_STRING) {
                value = text.substring(0, MAX_STRING);
            }
            out.put(entry.getKey(), value);
        }
        return out;
    }
}

package com.g3cs.integration.service.impl;

import com.g3cs.integration.model.AuditLogsDetails;
import com.g3cs.integration.service.AuditService;
import com.g3cs.integration.service.SequenceGeneratorService;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuditServiceImpl implements AuditService {

    private final MongoTemplate mongoTemplate;
    private final SequenceGeneratorService sequenceGeneratorService;

    public AuditServiceImpl(MongoTemplate mongoTemplate, SequenceGeneratorService sequenceGeneratorService) {
        this.mongoTemplate = mongoTemplate;
        this.sequenceGeneratorService = sequenceGeneratorService;
    }

    @Override
    public void record(String entityType, String entityId, String fromStatus, String toStatus,
                       String messageCode, String message, Map<String, Object> additionalData) {
        String auditId;
        try {
            auditId = sequenceGeneratorService.generateNextNumber("AUDIT", entityId);
        } catch (Exception e) {
            auditId = "AUD-" + Instant.now().toEpochMilli();
        }
        AuditLogsDetails audit = AuditLogsDetails.builder()
                .auditId(auditId)
                .entityType(entityType)
                .entityId(entityId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .performedBy(TenantContext.getUserId())
                .messageCode(messageCode)
                .message(message)
                .additionalData(additionalData)
                .createdDt(Instant.now())
                .build();
        mongoTemplate.save(audit);
    }
}

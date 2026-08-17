package com.g3cs.integration.service;

import java.util.Map;

public interface AuditService {
    void record(String entityType, String entityId, String fromStatus, String toStatus,
                String messageCode, String message, Map<String, Object> additionalData);
}

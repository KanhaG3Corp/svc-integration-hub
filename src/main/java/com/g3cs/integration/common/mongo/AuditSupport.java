package com.g3cs.integration.common.mongo;

import com.g3cs.integration.tenant.TenantContext;

import java.time.Instant;

public final class AuditSupport {

    private AuditSupport() {}

    public static void onCreate(AuditableDocument document) {
        Instant now = Instant.now();
        String userId = TenantContext.getUserId();
        document.setCreatedBy(userId);
        document.setCreatedDt(now);
        document.setUpdatedBy(userId);
        document.setUpdatedDt(now);
        document.setIsDeleted(false);
    }

    public static void onUpdate(AuditableDocument document) {
        document.setUpdatedBy(TenantContext.getUserId());
        document.setUpdatedDt(Instant.now());
    }
}

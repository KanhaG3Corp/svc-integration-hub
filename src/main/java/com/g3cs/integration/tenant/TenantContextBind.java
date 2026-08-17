package com.g3cs.integration.tenant;

import java.util.Locale;

/**
 * Binds tenant Mongo routing for scheduled sync jobs that have no inbound JWT.
 */
public final class TenantContextBind {

    private TenantContextBind() {}

    public static void bindFromTenantId(String tenantId, String tenantDb, String masterDatabaseName) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required for scheduled sync");
        }
        String trimmed = tenantId.trim();
        if (tenantDb != null && !tenantDb.isBlank()) {
            TenantContext.setTenantId(trimmed);
            TenantContext.setTenantDB(tenantDb.trim());
            return;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (TenantContext.isMasterTenant(trimmed)) {
            TenantContext.setTenantId(trimmed);
            TenantContext.setTenantDB(masterDatabaseName);
            return;
        }
        String db = lower.endsWith("_db") ? lower : lower + "_db";
        TenantContext.setTenantId(trimmed);
        TenantContext.setTenantDB(db);
    }
}

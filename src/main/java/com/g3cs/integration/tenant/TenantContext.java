package com.g3cs.integration.tenant;

/**
 * Thread-local tenant and user identity from the JWT (set by AuthFilter).
 * Used by TenantAwareMongoDatabaseFactory to route MongoDB per request.
 */
public final class TenantContext {

    public static final String MASTER_TENANT_ID = "G3_SecAi";
    public static final String MASTER_TENANT_DB = "G3_SecAi_DB";

    private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_DB = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    public static void setTenantDB(String tenantDb) {
        CURRENT_TENANT_DB.set(tenantDb);
    }

    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static String getTenantDB() {
        return CURRENT_TENANT_DB.get();
    }

    public static String getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static boolean isMasterTenant(String tenantIdOrDb) {
        if (tenantIdOrDb == null || tenantIdOrDb.isBlank()) {
            return false;
        }
        String normalized = tenantIdOrDb.trim();
        return MASTER_TENANT_ID.equalsIgnoreCase(normalized)
                || MASTER_TENANT_DB.equalsIgnoreCase(normalized)
                || "g3_secai".equalsIgnoreCase(normalized)
                || "g3_secai_db".equalsIgnoreCase(normalized);
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
        CURRENT_TENANT_DB.remove();
        CURRENT_USER_ID.remove();
    }
}

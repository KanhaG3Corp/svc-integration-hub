package com.g3cs.integration.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.lang.NonNull;

import java.util.Objects;

/**
 * Routes each Mongo operation to the tenant database stored in TenantContext.
 */
public class TenantAwareMongoDatabaseFactory extends SimpleMongoClientDatabaseFactory {

    private final String defaultDatabase;

    public TenantAwareMongoDatabaseFactory(MongoClient mongoClient, String defaultDatabase) {
        super(mongoClient, defaultDatabase);
        this.defaultDatabase = defaultDatabase;
    }

    @Override
    @NonNull
    public MongoDatabase getMongoDatabase() throws DataAccessException {
        String tenantDbName = TenantContext.getTenantDB();
        if (tenantDbName == null || tenantDbName.isBlank()) {
            throw new IllegalStateException("Tenant DB name is required — ensure Authorization JWT is sent");
        }
        if (TenantContext.isMasterTenant(tenantDbName)
                || Objects.requireNonNull(defaultDatabase).equalsIgnoreCase(tenantDbName)) {
            return getMongoDatabase(defaultDatabase);
        }
        return getMongoDatabase(tenantDbName);
    }

    public String getDefaultDatabase() {
        return defaultDatabase;
    }
}

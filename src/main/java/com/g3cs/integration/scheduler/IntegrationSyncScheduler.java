package com.g3cs.integration.scheduler;

import com.g3cs.integration.common.enums.IntegrationStatus;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.platform.TenantManagement;
import com.g3cs.integration.service.SyncEngineService;
import com.g3cs.integration.service.TenantDirectoryService;
import com.g3cs.integration.tenant.TenantContext;
import com.g3cs.integration.tenant.TenantContextBind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "integration.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationSyncScheduler {

    private final TenantDirectoryService tenantDirectoryService;
    private final MongoTemplate mongoTemplate;
    private final SyncEngineService syncEngineService;
    private final String masterDatabaseName;

    public IntegrationSyncScheduler(TenantDirectoryService tenantDirectoryService,
                                    MongoTemplate mongoTemplate,
                                    SyncEngineService syncEngineService,
                                    @Value("${spring.data.mongodb.database}") String masterDatabaseName) {
        this.tenantDirectoryService = tenantDirectoryService;
        this.mongoTemplate = mongoTemplate;
        this.syncEngineService = syncEngineService;
        this.masterDatabaseName = masterDatabaseName;
    }

    @Scheduled(cron = "${integration.scheduler.cron:0 */1 * * * *}")
    public void runDueIntegrations() {
        List<TenantManagement> tenants;
        try {
            tenants = tenantDirectoryService.findActiveTenantsForBackgroundJobs();
        } catch (Exception e) {
            log.warn("Unable to load tenant directory for integration scheduler: {}", e.getMessage());
            return;
        } finally {
            TenantContext.clear();
        }
        Instant now = Instant.now();
        for (TenantManagement tenant : tenants) {
            TenantContextBind.bindFromTenantId(tenant.getTenantId(), tenant.getTenantDb(), masterDatabaseName);
            try {
                Query query = Query.query(Criteria.where("status").is(IntegrationStatus.ACTIVE)
                        .and("is_deleted").ne(true)
                        .and("next_run_at").lte(now));
                List<IntegrationDocument> due = mongoTemplate.find(query, IntegrationDocument.class);
                for (IntegrationDocument integration : due) {
                    try {
                        syncEngineService.run(integration, "SCHEDULER");
                    } catch (Exception e) {
                        log.error("Scheduled sync failed tenant={} integration={}",
                                tenant.getTenantId(), integration.getIntegrationId(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Scheduled sync scan failed tenant={}", tenant.getTenantId(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}

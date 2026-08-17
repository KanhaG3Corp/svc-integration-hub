package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.ConflictPolicy;
import com.g3cs.integration.common.enums.IntegrationOperation;
import com.g3cs.integration.common.enums.IntegrationStatus;
import com.g3cs.integration.common.enums.SyncDirection;
import com.g3cs.integration.common.enums.SyncMode;
import com.g3cs.integration.common.enums.TransformMode;
import com.g3cs.integration.common.mongo.AuditableDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "integrations")
public class IntegrationDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("integration_id")
    private String integrationId;
    @Field("tenant_id")
    private String tenantId;
    @Field("tenant_db")
    private String tenantDb;
    @Field("name")
    private String name;
    @Field("description")
    private String description;
    @Field("connection_id")
    private String connectionId;
    @Field("application_id")
    private String applicationId;
    @Field("tprm_module_key")
    private String tprmModuleKey;
    @Field("remote_resource_key")
    private String remoteResourceKey;
    @Field("operation")
    private IntegrationOperation operation;
    @Field("direction")
    private SyncDirection direction;
    @Field("conflict_policy")
    private ConflictPolicy conflictPolicy;
    @Field("sync_mode")
    private SyncMode syncMode;
    @Field("pagination_enabled")
    private Boolean paginationEnabled;
    @Field("batch_size")
    private Integer batchSize;
    @Field("periodic_interval_minutes")
    private Integer periodicIntervalMinutes;
    @Field("schedule_cron")
    private String scheduleCron;
    @Field("schedule_timezone")
    private String scheduleTimezone;
    @Field("incremental_enabled")
    private Boolean incrementalEnabled;
    @Field("mapping")
    private List<FieldMapping> mapping;
    @Field("mapping_version")
    private Integer mappingVersion;
    @Field("status")
    private IntegrationStatus status;
    @Field("next_run_at")
    private Instant nextRunAt;
    @Field("last_write_date")
    private Instant lastWriteDate;
    @Field("last_offset")
    private Integer lastOffset;
    @Field("last_external_id")
    private String lastExternalId;
    @Field("last_successful_at")
    private Instant lastSuccessfulAt;
    @Field("remote_domain")
    private String remoteDomain;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMapping {
        private String remoteField;
        private String tprmField;
        private TransformMode transformMode;
        private boolean included;
        private Map<String, Object> transformConfig;
    }
}

package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.FailedRecordStatus;
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
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "integration_failed_records")
public class IntegrationFailedRecordDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("failed_record_id")
    private String failedRecordId;
    @Field("tenant_id")
    private String tenantId;
    @Field("integration_id")
    private String integrationId;
    @Field("execution_id")
    private String executionId;
    @Field("external_id")
    private String externalId;
    @Field("tprm_module_key")
    private String tprmModuleKey;
    @Field("payload_snapshot")
    private Map<String, Object> payloadSnapshot;
    @Field("error_code")
    private String errorCode;
    @Field("error_message")
    private String errorMessage;
    @Field("status")
    private FailedRecordStatus status;
    @Field("retry_count")
    private Integer retryCount;
    @Field("last_retry_at")
    private Instant lastRetryAt;
}

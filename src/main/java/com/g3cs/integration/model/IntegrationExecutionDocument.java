package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.ExecutionStatus;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "integration_executions")
public class IntegrationExecutionDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("execution_id")
    private String executionId;
    @Field("tenant_id")
    private String tenantId;
    @Field("integration_id")
    private String integrationId;
    @Field("status")
    private ExecutionStatus status;
    @Field("triggered_by")
    private String triggeredBy;
    @Field("started_at")
    private Instant startedAt;
    @Field("finished_at")
    private Instant finishedAt;
    @Field("records_read")
    private Integer recordsRead;
    @Field("records_inserted")
    private Integer recordsInserted;
    @Field("records_updated")
    private Integer recordsUpdated;
    @Field("records_failed")
    private Integer recordsFailed;
    @Field("error_code")
    private String errorCode;
    @Field("error_message")
    private String errorMessage;
}

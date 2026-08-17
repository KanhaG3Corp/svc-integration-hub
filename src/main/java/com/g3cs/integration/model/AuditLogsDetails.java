package com.g3cs.integration.model;

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
@Document(collection = "stage_audit_logs")
public class AuditLogsDetails {
    @Id
    private String id;
    @Field("audit_id")
    private String auditId;
    @Field("entity_type")
    private String entityType;
    @Field("entity_id")
    private String entityId;
    @Field("from_status")
    private String fromStatus;
    @Field("to_status")
    private String toStatus;
    @Field("performed_by")
    private String performedBy;
    @Field("message_code")
    private String messageCode;
    @Field("message")
    private String message;
    @Field("additional_data")
    private Map<String, Object> additionalData;
    @Field("created_dt")
    private Instant createdDt;
}

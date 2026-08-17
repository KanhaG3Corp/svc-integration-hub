package com.g3cs.integration.model;

import com.g3cs.integration.common.mongo.AuditableDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "connection_credentials")
public class ConnectionCredentialDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("credential_reference")
    private String credentialReference;
    @Field("tenant_id")
    private String tenantId;
    @Field("encrypted_payload")
    private String encryptedPayload;
}

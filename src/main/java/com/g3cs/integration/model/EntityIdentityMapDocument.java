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
@Document(collection = "entity_identity_map")
public class EntityIdentityMapDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("tenant_id")
    private String tenantId;
    @Field("integration_id")
    private String integrationId;
    @Field("remote_resource_key")
    private String remoteResourceKey;
    @Field("external_id")
    private String externalId;
    @Field("tprm_module_key")
    private String tprmModuleKey;
    @Field("tprm_record_id")
    private String tprmRecordId;
}

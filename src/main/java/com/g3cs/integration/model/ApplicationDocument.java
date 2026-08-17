package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.ConnectorCapability;
import com.g3cs.integration.common.mongo.AuditableDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "applications")
public class ApplicationDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("application_id")
    private String applicationId;
    @Field("name")
    private String name;
    @Field("protocol")
    private String protocol;
    @Field("status")
    private String status;
    @Field("supported_capabilities")
    private List<ConnectorCapability> supportedCapabilities;
}

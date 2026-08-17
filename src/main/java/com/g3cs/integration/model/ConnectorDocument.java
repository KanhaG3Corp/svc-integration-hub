package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.enums.ConnectorCapability;
import com.g3cs.integration.common.enums.IncrementalStrategy;
import com.g3cs.integration.common.enums.PaginationStrategy;
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
@Document(collection = "connectors")
public class ConnectorDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("connector_id")
    private String connectorId;
    @Field("application_id")
    private String applicationId;
    @Field("name")
    private String name;
    @Field("status")
    private String status;
    @Field("capabilities")
    private List<ConnectorCapability> capabilities;
    @Field("supported_auth_types")
    private List<AuthenticationType> supportedAuthTypes;
    @Field("pagination_strategy")
    private PaginationStrategy paginationStrategy;
    @Field("incremental_strategy")
    private IncrementalStrategy incrementalStrategy;
}

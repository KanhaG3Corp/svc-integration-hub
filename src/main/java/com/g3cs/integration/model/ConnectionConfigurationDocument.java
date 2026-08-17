package com.g3cs.integration.model;

import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.enums.ConnectionStatus;
import com.g3cs.integration.common.enums.HealthStatus;
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
@Document(collection = "connection_configurations")
public class ConnectionConfigurationDocument extends AuditableDocument {
    @Id
    private String id;
    @Field("connection_id")
    private String connectionId;
    @Field("tenant_id")
    private String tenantId;
    @Field("application_id")
    private String applicationId;
    @Field("connector_id")
    private String connectorId;
    @Field("name")
    private String name;
    @Field("protocol")
    private String protocol;
    @Field("authentication_type")
    private AuthenticationType authenticationType;
    @Field("endpoint")
    private Endpoint endpoint;
    @Field("credential_reference")
    private String credentialReference;
    @Field("capabilities")
    private List<Map<String, Object>> capabilities;
    @Field("rate_limit")
    private RateLimit rateLimit;
    @Field("status")
    private ConnectionStatus status;
    @Field("health_status")
    private HealthStatus healthStatus;
    @Field("last_tested_at")
    private Instant lastTestedAt;
    @Field("last_test_status")
    private String lastTestStatus;
    @Field("last_test_error_code")
    private String lastTestErrorCode;
    @Field("last_successful_at")
    private Instant lastSuccessfulAt;
    @Field("odoo_version")
    private String odooVersion;
    @Field("settings")
    private Map<String, String> settings;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Endpoint {
        private String baseUrl;
        private String database;
        private String region;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimit {
        private Integer maxConcurrency;
        private Integer requestsPerSecond;
        private Integer burstLimit;
    }
}

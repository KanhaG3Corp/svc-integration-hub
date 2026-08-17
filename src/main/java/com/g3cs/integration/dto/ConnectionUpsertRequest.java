package com.g3cs.integration.dto;

import com.g3cs.integration.common.enums.AuthenticationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ConnectionUpsertRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String applicationId;
    @NotBlank
    private String connectorId;
    @NotNull
    private AuthenticationType authenticationType;
    @NotBlank
    private String baseUrl;
    private String database;
    private Map<String, String> credentials;
    private Map<String, String> settings;
}

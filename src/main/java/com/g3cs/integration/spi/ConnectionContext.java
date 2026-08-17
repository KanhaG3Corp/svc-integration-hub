package com.g3cs.integration.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionContext {
    private String baseUrl;
    private String database;
    private String connectorId;
    private String authenticationType;
    private Map<String, String> secrets;
    private Map<String, String> settings;
}

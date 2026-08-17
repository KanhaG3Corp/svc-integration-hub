package com.g3cs.integration.spi;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {

    private final Map<String, RemoteConnector> connectors;

    public ConnectorRegistry(List<RemoteConnector> connectors) {
        this.connectors = connectors.stream()
                .collect(Collectors.toMap(RemoteConnector::connectorId, Function.identity()));
    }

    public RemoteConnector require(String connectorId) {
        RemoteConnector connector = connectors.get(connectorId);
        if (connector == null) {
            throw new IllegalArgumentException("Unsupported connector: " + connectorId);
        }
        return connector;
    }
}

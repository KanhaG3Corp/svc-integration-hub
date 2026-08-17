package com.g3cs.integration.spi;

import java.util.List;
import java.util.Map;

public interface RemoteConnector {
    String connectorId();

    ConnectionTestResult test(ConnectionContext context);

    List<RemoteResource> discoverResources(ConnectionContext context, String filter);

    List<RemoteField> discoverFields(ConnectionContext context, String resourceKey);

    ReadPage searchRead(ConnectionContext context, ReadRequest request);

    Object create(ConnectionContext context, String resourceKey, Map<String, Object> values);

    void write(ConnectionContext context, String resourceKey, String externalId, Map<String, Object> values);
}

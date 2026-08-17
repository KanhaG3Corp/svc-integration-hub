package com.g3cs.integration.service;

import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.dto.ConnectionUpsertRequest;
import com.g3cs.integration.model.ConnectionConfigurationDocument;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.spi.ConnectionTestResult;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.RemoteField;
import com.g3cs.integration.spi.RemoteResource;

import java.util.List;

public interface ConnectionService {
    ConnectionConfigurationDocument create(ConnectionUpsertRequest request);

    ConnectionConfigurationDocument update(String connectionId, ConnectionUpsertRequest request);

    ConnectionConfigurationDocument get(String connectionId);

    PagedResponseDto<ConnectionConfigurationDocument> list(PageQueryRequestDto query);

    ConnectionTestResult test(String connectionId);

    ConnectionConfigurationDocument activate(String connectionId);

    ConnectionConfigurationDocument disable(String connectionId);

    void delete(String connectionId);

    List<RemoteResource> discoverResources(String connectionId, String filter);

    List<RemoteField> discoverFields(String connectionId, String resourceKey);

    ReadPage sample(String connectionId, String resourceKey);

    ConnectionContext toContext(ConnectionConfigurationDocument connection);
}

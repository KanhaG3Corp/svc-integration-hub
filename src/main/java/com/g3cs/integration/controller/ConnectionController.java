package com.g3cs.integration.controller;

import com.g3cs.integration.common.dto.ApiResponseDto;
import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.dto.ConnectionUpsertRequest;
import com.g3cs.integration.model.ConnectionConfigurationDocument;
import com.g3cs.integration.service.ConnectionService;
import com.g3cs.integration.spi.ConnectionTestResult;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.RemoteField;
import com.g3cs.integration.spi.RemoteResource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integration-hub/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping
    public ApiResponseDto<ConnectionConfigurationDocument> create(@Valid @RequestBody ConnectionUpsertRequest request) {
        ConnectionConfigurationDocument created = connectionService.create(request);
        return ApiResponseDto.success(
                MessageCode.CONNECTION_CREATED.resolve(Map.of("name", created.getName())), created);
    }

    @PutMapping("/{connectionId}")
    public ApiResponseDto<ConnectionConfigurationDocument> update(
            @PathVariable String connectionId,
            @Valid @RequestBody ConnectionUpsertRequest request) {
        ConnectionConfigurationDocument updated = connectionService.update(connectionId, request);
        return ApiResponseDto.success(
                MessageCode.CONNECTION_UPDATED.resolve(Map.of("name", updated.getName())), updated);
    }

    @GetMapping("/{connectionId}")
    public ApiResponseDto<ConnectionConfigurationDocument> get(@PathVariable String connectionId) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), connectionService.get(connectionId));
    }

    @GetMapping
    public ApiResponseDto<PagedResponseDto<ConnectionConfigurationDocument>> list(PageQueryRequestDto query) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), connectionService.list(query));
    }

    @PostMapping("/{connectionId}/test")
    public ApiResponseDto<ConnectionTestResult> test(@PathVariable String connectionId) {
        ConnectionTestResult result = connectionService.test(connectionId);
        String message = result.isSuccess()
                ? MessageCode.CONNECTION_TEST_SUCCESS.resolve(Map.of("name", connectionId))
                : result.getErrorMessage();
        return ApiResponseDto.success(message, result);
    }

    @PostMapping("/{connectionId}/activate")
    public ApiResponseDto<ConnectionConfigurationDocument> activate(@PathVariable String connectionId) {
        ConnectionConfigurationDocument document = connectionService.activate(connectionId);
        return ApiResponseDto.success(
                MessageCode.CONNECTION_TEST_SUCCESS.resolve(Map.of("name", document.getName())), document);
    }

    @PostMapping("/{connectionId}/disable")
    public ApiResponseDto<ConnectionConfigurationDocument> disable(@PathVariable String connectionId) {
        ConnectionConfigurationDocument document = connectionService.disable(connectionId);
        return ApiResponseDto.success(
                MessageCode.CONNECTION_DISABLED.resolve(Map.of("name", document.getName())), document);
    }

    @DeleteMapping("/{connectionId}")
    public ApiResponseDto<Void> delete(@PathVariable String connectionId) {
        connectionService.delete(connectionId);
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), null);
    }

    @GetMapping("/{connectionId}/resources")
    public ApiResponseDto<List<RemoteResource>> resources(
            @PathVariable String connectionId,
            @RequestParam(required = false) String q) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                connectionService.discoverResources(connectionId, q));
    }

    @GetMapping("/{connectionId}/resources/{resourceKey:.+}/fields")
    public ApiResponseDto<List<RemoteField>> fields(
            @PathVariable String connectionId,
            @PathVariable String resourceKey) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                connectionService.discoverFields(connectionId, resourceKey));
    }

    @GetMapping("/{connectionId}/resources/{resourceKey:.+}/sample")
    public ApiResponseDto<ReadPage> sample(
            @PathVariable String connectionId,
            @PathVariable String resourceKey) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                connectionService.sample(connectionId, resourceKey));
    }
}

package com.g3cs.integration.controller;

import com.g3cs.integration.common.dto.ApiResponseDto;
import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.dto.IntegrationUpsertRequest;
import com.g3cs.integration.dto.RetryFailedRecordsRequest;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.IntegrationExecutionDocument;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;
import com.g3cs.integration.service.IntegrationService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/integration-hub/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping
    public ApiResponseDto<IntegrationDocument> create(@Valid @RequestBody IntegrationUpsertRequest request) {
        IntegrationDocument created = integrationService.create(request);
        return ApiResponseDto.success(
                MessageCode.INTEGRATION_CREATED.resolve(Map.of("name", created.getName())), created);
    }

    @PutMapping("/{integrationId}")
    public ApiResponseDto<IntegrationDocument> update(
            @PathVariable String integrationId,
            @Valid @RequestBody IntegrationUpsertRequest request) {
        IntegrationDocument updated = integrationService.update(integrationId, request);
        return ApiResponseDto.success(
                MessageCode.INTEGRATION_UPDATED.resolve(Map.of("name", updated.getName())), updated);
    }

    @GetMapping("/{integrationId}")
    public ApiResponseDto<IntegrationDocument> get(@PathVariable String integrationId) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), integrationService.get(integrationId));
    }

    @GetMapping
    public ApiResponseDto<PagedResponseDto<IntegrationDocument>> list(PageQueryRequestDto query) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), integrationService.list(query));
    }

    @PostMapping("/{integrationId}/mapping")
    public ApiResponseDto<IntegrationDocument> mapping(
            @PathVariable String integrationId,
            @RequestBody List<IntegrationDocument.FieldMapping> mapping) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                integrationService.saveMapping(integrationId, mapping));
    }

    @PostMapping("/{integrationId}/preview")
    public ApiResponseDto<Map<String, Object>> preview(@PathVariable String integrationId) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), integrationService.preview(integrationId));
    }

    @PostMapping("/{integrationId}/activate")
    public ApiResponseDto<IntegrationDocument> activate(@PathVariable String integrationId) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), integrationService.activate(integrationId));
    }

    @PostMapping("/{integrationId}/deactivate")
    public ApiResponseDto<IntegrationDocument> deactivate(@PathVariable String integrationId) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), integrationService.deactivate(integrationId));
    }

    @PostMapping("/{integrationId}/sync")
    public ApiResponseDto<IntegrationExecutionDocument> sync(@PathVariable String integrationId) {
        IntegrationExecutionDocument execution = integrationService.sync(integrationId);
        return ApiResponseDto.success(MessageCode.INTEGRATION_SYNC_STARTED.resolve(
                Map.of("name", integrationId)), execution);
    }

    @GetMapping("/{integrationId}/executions")
    public ApiResponseDto<PagedResponseDto<IntegrationExecutionDocument>> executions(
            @PathVariable String integrationId,
            PageQueryRequestDto query) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                integrationService.executions(integrationId, query));
    }

    @GetMapping("/{integrationId}/failed-records")
    public ApiResponseDto<PagedResponseDto<IntegrationFailedRecordDocument>> failedRecords(
            @PathVariable String integrationId,
            @RequestParam(required = false) String executionId,
            PageQueryRequestDto query) {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                integrationService.failedRecords(integrationId, executionId, query));
    }

    @PostMapping("/{integrationId}/failed-records/retry")
    public ApiResponseDto<Void> retrySelected(
            @PathVariable String integrationId,
            @RequestBody RetryFailedRecordsRequest request) {
        integrationService.retrySelected(integrationId, request);
        return ApiResponseDto.success(MessageCode.RETRY_QUEUED.template(), null);
    }

    @PostMapping("/{integrationId}/failed-records/retry-all")
    public ApiResponseDto<Void> retryAll(
            @PathVariable String integrationId,
            @RequestParam(required = false) String executionId) {
        integrationService.retryAll(integrationId, executionId);
        return ApiResponseDto.success(MessageCode.RETRY_QUEUED.template(), null);
    }
}

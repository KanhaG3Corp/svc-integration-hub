package com.g3cs.integration.service;

import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;

import java.util.List;
import java.util.Map;

public interface FailedRecordService {
    void recordFailure(String integrationId, String executionId, String tprmModuleKey, String externalId,
                       Map<String, Object> payload, String errorCode, String errorMessage);

    PagedResponseDto<IntegrationFailedRecordDocument> list(String integrationId, String executionId,
                                                            PageQueryRequestDto query);

    IntegrationFailedRecordDocument require(String failedRecordId);

    void markRetrying(List<IntegrationFailedRecordDocument> records);

    void markResolved(IntegrationFailedRecordDocument record);
}

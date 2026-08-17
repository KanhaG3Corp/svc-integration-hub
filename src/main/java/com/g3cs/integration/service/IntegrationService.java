package com.g3cs.integration.service;

import com.g3cs.integration.common.dto.PageQueryRequestDto;
import com.g3cs.integration.common.dto.PagedResponseDto;
import com.g3cs.integration.dto.IntegrationUpsertRequest;
import com.g3cs.integration.dto.RetryFailedRecordsRequest;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.IntegrationExecutionDocument;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;
import java.util.List;
import java.util.Map;

public interface IntegrationService {
    IntegrationDocument create(IntegrationUpsertRequest request);

    IntegrationDocument update(String integrationId, IntegrationUpsertRequest request);

    IntegrationDocument get(String integrationId);

    PagedResponseDto<IntegrationDocument> list(PageQueryRequestDto query);

    IntegrationDocument saveMapping(String integrationId, List<IntegrationDocument.FieldMapping> mapping);

    IntegrationDocument activate(String integrationId);

    IntegrationDocument deactivate(String integrationId);

    Map<String, Object> preview(String integrationId);

    IntegrationExecutionDocument sync(String integrationId);

    PagedResponseDto<IntegrationExecutionDocument> executions(String integrationId, PageQueryRequestDto query);

    PagedResponseDto<IntegrationFailedRecordDocument> failedRecords(String integrationId, String executionId,
                                                                     PageQueryRequestDto query);

    void retrySelected(String integrationId, RetryFailedRecordsRequest request);

    void retryAll(String integrationId, String executionId);
}

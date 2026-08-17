package com.g3cs.integration.service;

import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.IntegrationExecutionDocument;
import com.g3cs.integration.model.IntegrationFailedRecordDocument;

import java.util.List;
import java.util.Map;

public interface SyncEngineService {
    IntegrationExecutionDocument run(IntegrationDocument integration, String triggeredBy);

    boolean processRemoteRecord(IntegrationDocument integration, String executionId, Map<String, Object> remoteRecord);

    void retryRecords(IntegrationDocument integration, List<IntegrationFailedRecordDocument> records);
}

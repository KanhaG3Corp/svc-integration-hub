package com.g3cs.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RetryFailedRecordsRequest {
    private List<String> failedRecordIds;
    private String executionId;
}

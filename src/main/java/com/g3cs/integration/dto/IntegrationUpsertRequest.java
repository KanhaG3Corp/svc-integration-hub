package com.g3cs.integration.dto;

import com.g3cs.integration.common.enums.ConflictPolicy;
import com.g3cs.integration.common.enums.IntegrationOperation;
import com.g3cs.integration.common.enums.SyncDirection;
import com.g3cs.integration.common.enums.SyncMode;
import com.g3cs.integration.model.IntegrationDocument;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IntegrationUpsertRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String connectionId;
    private String tprmModuleKey;
    private String remoteResourceKey;
    private IntegrationOperation operation;
    private SyncDirection direction;
    private ConflictPolicy conflictPolicy;
    private SyncMode syncMode;
    private Boolean paginationEnabled;
    private Integer batchSize;
    private Integer periodicIntervalMinutes;
    private String scheduleCron;
    private String scheduleTimezone;
    private Boolean incrementalEnabled;
    private String remoteDomain;
    private List<IntegrationDocument.FieldMapping> mapping;
}

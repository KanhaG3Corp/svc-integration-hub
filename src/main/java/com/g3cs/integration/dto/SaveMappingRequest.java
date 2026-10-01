package com.g3cs.integration.dto;

import com.g3cs.integration.model.IntegrationDocument;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveMappingRequest {
    private List<IntegrationDocument.FieldMapping> mapping;
}

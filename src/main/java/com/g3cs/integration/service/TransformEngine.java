package com.g3cs.integration.service;

import com.g3cs.integration.common.enums.TransformMode;
import com.g3cs.integration.model.IntegrationDocument;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface TransformEngine {
    Map<String, Object> transform(Map<String, Object> remoteRecord, List<IntegrationDocument.FieldMapping> mapping);
    Object extractRemoteValue(Map<String, Object> remoteRecord, String remoteField, TransformMode mode,
                              Map<String, Object> config);
}

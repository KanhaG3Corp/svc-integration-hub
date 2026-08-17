package com.g3cs.integration.service;

import com.g3cs.integration.model.IntegrationDocument;

import java.util.List;
import java.util.Map;

public interface MasterValueResolver {
    void applyResolves(Map<String, Object> transformed, List<IntegrationDocument.FieldMapping> mapping);

    record EntityResolve(String entityId, String regionId) {}

    EntityResolve resolveEntity(String name);
}

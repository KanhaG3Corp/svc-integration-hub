package com.g3cs.integration.service;

import java.util.Map;

public interface VendorPersistService {
    UpsertResult upsert(Map<String, Object> transformed, String integrationId, String remoteResourceKey, String externalId);

    record UpsertResult(boolean inserted, String vendorId) {}
}

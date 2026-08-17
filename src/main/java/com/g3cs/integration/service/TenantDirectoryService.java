package com.g3cs.integration.service;

import com.g3cs.integration.model.platform.TenantManagement;

import java.util.List;

public interface TenantDirectoryService {
    List<TenantManagement> findActiveTenantsForBackgroundJobs();
}

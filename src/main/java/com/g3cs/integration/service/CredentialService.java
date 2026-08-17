package com.g3cs.integration.service;

import java.util.Map;

public interface CredentialService {
    String store(Map<String, String> secrets);
    void replace(String credentialReference, Map<String, String> secrets);
    Map<String, String> load(String credentialReference);
    void delete(String credentialReference);
}

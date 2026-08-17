package com.g3cs.integration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.common.mongo.AuditSupport;
import com.g3cs.integration.model.ConnectionCredentialDocument;
import com.g3cs.integration.service.CredentialService;
import com.g3cs.integration.tenant.TenantContext;
import com.g3cs.integration.utils.AesGcmUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class CredentialServiceImpl implements CredentialService {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final String encryptionKey;

    public CredentialServiceImpl(MongoTemplate mongoTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${credential.encryption.key}") String encryptionKey) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.encryptionKey = encryptionKey;
    }

    @Override
    public String store(Map<String, String> secrets) {
        String reference = "cred-" + UUID.randomUUID();
        ConnectionCredentialDocument document = ConnectionCredentialDocument.builder()
                .credentialReference(reference)
                .tenantId(TenantContext.getTenantId())
                .encryptedPayload(encrypt(secrets))
                .build();
        AuditSupport.onCreate(document);
        mongoTemplate.save(document);
        return reference;
    }

    @Override
    public void replace(String credentialReference, Map<String, String> secrets) {
        ConnectionCredentialDocument document = findByReference(credentialReference);
        document.setEncryptedPayload(encrypt(secrets));
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
    }

    @Override
    public Map<String, String> load(String credentialReference) {
        ConnectionCredentialDocument document = findByReference(credentialReference);
        try {
            byte[] decrypted = AesGcmUtil.decrypt(document.getEncryptedPayload(), encryptionKey);
            return objectMapper.readValue(decrypted, MAP_TYPE);
        } catch (Exception e) {
            throw new BusinessException(MessageCode.INTERNAL_ERROR, Map.of(), 500);
        }
    }

    @Override
    public void delete(String credentialReference) {
        Query query = Query.query(Criteria.where("credential_reference").is(credentialReference)
                .and("is_deleted").ne(true));
        ConnectionCredentialDocument document = mongoTemplate.findOne(query, ConnectionCredentialDocument.class);
        if (document == null) {
            return;
        }
        document.setIsDeleted(true);
        AuditSupport.onUpdate(document);
        mongoTemplate.save(document);
    }

    private ConnectionCredentialDocument findByReference(String credentialReference) {
        Query query = Query.query(Criteria.where("credential_reference").is(credentialReference)
                .and("is_deleted").ne(true));
        ConnectionCredentialDocument document = mongoTemplate.findOne(query, ConnectionCredentialDocument.class);
        if (document == null) {
            throw new BusinessException(MessageCode.CONNECTION_NOT_FOUND, Map.of(), 404);
        }
        return document;
    }

    private String encrypt(Map<String, String> secrets) {
        try {
            return AesGcmUtil.encrypt(objectMapper.writeValueAsBytes(secrets == null ? Map.of() : secrets),
                    encryptionKey);
        } catch (Exception e) {
            throw new BusinessException(MessageCode.INTERNAL_ERROR, Map.of(), 500);
        }
    }
}

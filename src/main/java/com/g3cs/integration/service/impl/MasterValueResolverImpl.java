package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.enums.TransformMode;
import com.g3cs.integration.common.exception.RecordProcessingException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.model.BusinessParamDtl;
import com.g3cs.integration.model.EntityDetails;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.model.MasterData;
import com.g3cs.integration.service.MasterValueResolver;
import com.g3cs.integration.service.RedisService;
import com.g3cs.integration.tenant.TenantContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MasterValueResolverImpl implements MasterValueResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final MongoTemplate mongoTemplate;
    private final RedisService redisService;

    public MasterValueResolverImpl(MongoTemplate mongoTemplate, RedisService redisService) {
        this.mongoTemplate = mongoTemplate;
        this.redisService = redisService;
    }

    @Override
    public void applyResolves(Map<String, Object> transformed, List<IntegrationDocument.FieldMapping> mapping) {
        if (mapping == null) {
            return;
        }
        for (IntegrationDocument.FieldMapping row : mapping) {
            if (row == null || !row.isIncluded() || row.getTransformMode() == null) {
                continue;
            }
            String key = row.getTprmField();
            Object value = transformed.get(key);
            if (row.getTransformMode() == TransformMode.RESOLVE_ENTITY) {
                EntityResolve resolved = resolveEntity(asText(value));
                transformed.put("entityIds", List.of(resolved.entityId()));
                transformed.put("regionIds", resolved.regionId() == null ? List.of() : List.of(resolved.regionId()));
                transformed.put(key, resolved.entityId());
            } else if (row.getTransformMode() == TransformMode.RESOLVE_PARAM) {
                transformed.put(key, resolveParam(value));
            } else if (row.getTransformMode() == TransformMode.RESOLVE_MASTER) {
                transformed.put(key, resolveMaster(asText(value)));
            }
        }
    }

    @Override
    public EntityResolve resolveEntity(String name) {
        if (name == null || name.isBlank()) {
            throw new RecordProcessingException(MessageCode.ENTITY_RESOLVE_FAILED, Map.of("value", ""));
        }
        String needle = name.trim();
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("entity_name").regex("^" + java.util.regex.Pattern.quote(needle) + "$", "i"),
                Criteria.where("is_enable").is(true),
                Criteria.where("is_deleted").ne(true)
        ));
        List<EntityDetails> matches = mongoTemplate.find(query, EntityDetails.class);
        if (matches.size() != 1) {
            throw new RecordProcessingException(MessageCode.ENTITY_RESOLVE_FAILED, Map.of("value", needle));
        }
        EntityDetails entity = matches.get(0);
        return new EntityResolve(entity.getId(), entity.getRegionId());
    }

    private Object resolveParam(Object value) {
        if (value instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object item : list) {
                ids.add(resolveParamOne(asText(item)));
            }
            return ids;
        }
        return resolveParamOne(asText(value));
    }

    private String resolveParamOne(String label) {
        if (label == null || label.isBlank()) {
            throw new RecordProcessingException(MessageCode.MASTER_RESOLVE_FAILED, Map.of("value", ""));
        }
        String cacheKey = cachePrefix() + ":param:" + label.trim().toLowerCase(Locale.ROOT);
        return redisService.get(cacheKey, String.class).orElseGet(() -> {
            Query query = Query.query(new Criteria().andOperator(
                    Criteria.where("param_desc").regex("^" + java.util.regex.Pattern.quote(label.trim()) + "$", "i"),
                    Criteria.where("is_enable").is(true),
                    Criteria.where("is_deleted").ne(true)
            ));
            List<BusinessParamDtl> matches = mongoTemplate.find(query, BusinessParamDtl.class);
            if (matches.size() != 1) {
                throw new RecordProcessingException(MessageCode.MASTER_RESOLVE_FAILED, Map.of("value", label));
            }
            String id = matches.get(0).getId();
            redisService.set(cacheKey, id, CACHE_TTL);
            return id;
        });
    }

    private String resolveMaster(String label) {
        if (label == null || label.isBlank()) {
            throw new RecordProcessingException(MessageCode.MASTER_RESOLVE_FAILED, Map.of("value", ""));
        }
        String cacheKey = cachePrefix() + ":master:" + label.trim().toLowerCase(Locale.ROOT);
        return redisService.get(cacheKey, String.class).orElseGet(() -> {
            Query query = Query.query(new Criteria().andOperator(
                    Criteria.where("master_name").regex("^" + java.util.regex.Pattern.quote(label.trim()) + "$", "i"),
                    Criteria.where("is_enable").is(true),
                    Criteria.where("is_deleted").ne(true)
            ));
            List<MasterData> matches = mongoTemplate.find(query, MasterData.class);
            if (matches.size() != 1) {
                throw new RecordProcessingException(MessageCode.MASTER_RESOLVE_FAILED, Map.of("value", label));
            }
            String id = matches.get(0).getId();
            redisService.set(cacheKey, id, CACHE_TTL);
            return id;
        });
    }

    private String cachePrefix() {
        return "ih:masters:" + TenantContext.getTenantId();
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.size() == 2 ? list.get(1) : list.get(0));
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "false".equalsIgnoreCase(text) ? null : text;
    }
}

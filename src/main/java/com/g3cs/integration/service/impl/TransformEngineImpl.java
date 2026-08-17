package com.g3cs.integration.service.impl;

import com.g3cs.integration.common.enums.TransformMode;
import com.g3cs.integration.model.IntegrationDocument;
import com.g3cs.integration.service.TransformEngine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TransformEngineImpl implements TransformEngine {

    @Override
    public Map<String, Object> transform(Map<String, Object> remoteRecord, List<IntegrationDocument.FieldMapping> mapping) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (mapping == null) {
            return out;
        }
        for (IntegrationDocument.FieldMapping row : mapping) {
            if (row == null || !row.isIncluded() || row.getTprmField() == null || row.getTprmField().isBlank()) {
                continue;
            }
            TransformMode mode = row.getTransformMode() == null ? TransformMode.DIRECT : row.getTransformMode();
            out.put(row.getTprmField(), extractRemoteValue(remoteRecord, row.getRemoteField(), mode, row.getTransformConfig()));
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object extractRemoteValue(Map<String, Object> remoteRecord, String remoteField,
                                     TransformMode mode, Map<String, Object> config) {
        Map<String, Object> cfg = config == null ? Map.of() : config;
        return switch (mode) {
            case CONSTANT -> cfg.get("value");
            case CONCAT -> concat(remoteRecord, cfg);
            case SPLIT -> split(valueAt(remoteRecord, remoteField), cfg);
            case LOOKUP -> lookup(valueAt(remoteRecord, remoteField), cfg);
            case FORMAT -> format(valueAt(remoteRecord, remoteField), cfg);
            case CONVERT -> convert(valueAt(remoteRecord, remoteField), cfg);
            case CONDITION -> condition(valueAt(remoteRecord, remoteField), cfg);
            case DIRECT, RENAME, RESOLVE_ENTITY, RESOLVE_PARAM, RESOLVE_MASTER -> valueAt(remoteRecord, remoteField);
        };
    }

    private Object valueAt(Map<String, Object> remoteRecord, String remoteField) {
        if (remoteRecord == null || remoteField == null || remoteField.isBlank()) {
            return null;
        }
        Object value = remoteRecord.get(remoteField);
        if (value instanceof List<?> list && list.size() == 2) {
            return list.get(1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object concat(Map<String, Object> remoteRecord, Map<String, Object> cfg) {
        Object sources = cfg.get("sources");
        String separator = cfg.get("separator") == null ? " " : String.valueOf(cfg.get("separator"));
        List<String> parts = new ArrayList<>();
        if (sources instanceof List<?> list) {
            for (Object source : list) {
                Object value = valueAt(remoteRecord, String.valueOf(source));
                if (value != null && !String.valueOf(value).isBlank()) {
                    parts.add(String.valueOf(value));
                }
            }
        }
        return String.join(separator, parts);
    }

    private Object split(Object value, Map<String, Object> cfg) {
        if (value == null) {
            return null;
        }
        String delimiter = cfg.get("delimiter") == null ? "," : String.valueOf(cfg.get("delimiter"));
        int index = cfg.get("index") instanceof Number n ? n.intValue() : 0;
        String[] parts = String.valueOf(value).split(delimiter);
        if (index < 0 || index >= parts.length) {
            return null;
        }
        return parts[index].trim();
    }

    @SuppressWarnings("unchecked")
    private Object lookup(Object value, Map<String, Object> cfg) {
        if (value == null) {
            return null;
        }
        Object table = cfg.get("table");
        if (table instanceof Map<?, ?> map) {
            Object mapped = map.get(String.valueOf(value));
            return mapped == null ? map.get(String.valueOf(value).toLowerCase(Locale.ROOT)) : mapped;
        }
        return value;
    }

    private Object format(Object value, Map<String, Object> cfg) {
        if (value == null) {
            return null;
        }
        String pattern = cfg.get("pattern") == null ? null : String.valueOf(cfg.get("pattern"));
        if (pattern == null || pattern.isBlank()) {
            return value;
        }
        try {
            if (pattern.contains("yyyy") || pattern.contains("HH")) {
                return DateTimeFormatter.ofPattern(pattern).format(parseDate(value));
            }
            return String.format(pattern, value);
        } catch (Exception e) {
            return value;
        }
    }

    private LocalDateTime parseDate(Object value) {
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        String text = String.valueOf(value).replace('T', ' ');
        if (text.length() >= 19) {
            return LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(text);
    }

    private Object convert(Object value, Map<String, Object> cfg) {
        String mode = cfg.get("mode") == null ? "display" : String.valueOf(cfg.get("mode"));
        if (value instanceof List<?> list && list.size() >= 2) {
            return "id".equalsIgnoreCase(mode) ? list.get(0) : list.get(1);
        }
        return value;
    }

    private Object condition(Object value, Map<String, Object> cfg) {
        String expected = cfg.get("equals") == null ? null : String.valueOf(cfg.get("equals"));
        boolean match = expected == null
                ? value != null && !String.valueOf(value).isBlank()
                : expected.equals(String.valueOf(value));
        return match ? cfg.get("then") : cfg.get("else");
    }
}

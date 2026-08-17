package com.g3cs.integration.spi.custom;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.service.impl.CatalogSeedServiceImpl;
import com.g3cs.integration.spi.AuthApplicator;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.spi.ConnectionTestResult;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.ReadRequest;
import com.g3cs.integration.spi.RemoteConnector;
import com.g3cs.integration.spi.RemoteField;
import com.g3cs.integration.spi.RemoteResource;
import com.g3cs.integration.utils.UrlSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CustomRestConnector implements RemoteConnector {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP = new TypeReference<>() {};

    private final RestClient.Builder restClientBuilder;
    private final AuthApplicator authApplicator;
    private final ObjectMapper objectMapper;

    public CustomRestConnector(RestClient.Builder restClientBuilder,
                               AuthApplicator authApplicator,
                               ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.authApplicator = authApplicator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String connectorId() {
        return CatalogSeedServiceImpl.CON_CUSTOM;
    }

    @Override
    public ConnectionTestResult test(ConnectionContext context) {
        try {
            String testPath = setting(context, "testPath", "/");
            JsonNode node = execute(context, "GET", testPath, null, Map.of());
            String version = node.path("version").asText("");
            return ConnectionTestResult.builder().success(true).version(version).build();
        } catch (BusinessException e) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .errorCode(e.getCode().name())
                    .errorMessage(e.getCode().resolve(e.getArgs()))
                    .build();
        } catch (Exception e) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .errorCode(MessageCode.CONNECTION_TEST_FAILED_UNKNOWN.name())
                    .errorMessage(MessageCode.CONNECTION_TEST_FAILED_UNKNOWN.resolve(Map.of("name", "Custom REST")))
                    .build();
        }
    }

    @Override
    public List<RemoteResource> discoverResources(ConnectionContext context, String filter) {
        String discoveryPath = setting(context, "discoveryPath", null);
        if (discoveryPath == null || discoveryPath.isBlank()) {
            return List.of();
        }
        JsonNode node = execute(context, "GET", discoveryPath, null, Map.of());
        List<RemoteResource> resources = new ArrayList<>();
        for (Map<String, Object> row : toList(node)) {
            String key = first(row, "key", "id", "name", "path");
            if (key == null) {
                continue;
            }
            if (filter != null && !filter.isBlank() && !key.toLowerCase().contains(filter.toLowerCase())) {
                continue;
            }
            resources.add(RemoteResource.builder()
                    .key(key)
                    .model(key)
                    .label(row.get("label") == null ? key : String.valueOf(row.get("label")))
                    .build());
        }
        return resources;
    }

    @Override
    public List<RemoteField> discoverFields(ConnectionContext context, String resourceKey) {
        String fieldsPath = setting(context, "fieldsPath", null);
        JsonNode node;
        if (fieldsPath != null && !fieldsPath.isBlank()) {
            node = execute(context, "GET", fieldsPath.replace("{resource}", resourceKey), null, Map.of());
        } else {
            ReadPage sample = searchRead(context, ReadRequest.builder()
                    .resourceKey(resourceKey)
                    .limit(1)
                    .offset(0)
                    .build());
            if (sample.getRecords().isEmpty()) {
                return List.of();
            }
            List<RemoteField> inferred = new ArrayList<>();
            sample.getRecords().get(0).forEach((key, value) -> inferred.add(RemoteField.builder()
                    .name(key)
                    .label(key)
                    .type(value == null ? "char" : value.getClass().getSimpleName())
                    .required(false)
                    .readonly(false)
                    .build()));
            return inferred;
        }
        List<RemoteField> fields = new ArrayList<>();
        if (node.isObject() && !node.isArray()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                fields.add(RemoteField.builder()
                        .name(entry.getKey())
                        .label(entry.getValue().path("label").asText(entry.getKey()))
                        .type(entry.getValue().path("type").asText("char"))
                        .required(entry.getValue().path("required").asBoolean(false))
                        .readonly(false)
                        .build());
            }
            return fields;
        }
        for (Map<String, Object> row : toList(node)) {
            String name = first(row, "name", "key", "id");
            if (name == null) {
                continue;
            }
            fields.add(RemoteField.builder()
                    .name(name)
                    .label(row.get("label") == null ? name : String.valueOf(row.get("label")))
                    .type(row.get("type") == null ? "char" : String.valueOf(row.get("type")))
                    .required(Boolean.TRUE.equals(row.get("required")))
                    .readonly(false)
                    .build());
        }
        return fields;
    }

    @Override
    public ReadPage searchRead(ConnectionContext context, ReadRequest request) {
        String path = resourcePath(context, request.getResourceKey());
        int limit = request.getLimit() == null ? 100 : request.getLimit();
        int offset = request.getOffset() == null ? 0 : request.getOffset();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("offset", String.valueOf(offset));
        query.put("limit", String.valueOf(limit));
        JsonNode node = execute(context, "GET", path, null, query);
        List<Map<String, Object>> records = toList(node);
        boolean hasMore = records.size() >= limit;
        return ReadPage.builder()
                .records(records)
                .hasMore(hasMore)
                .nextOffset(hasMore ? offset + limit : null)
                .build();
    }

    @Override
    public Object create(ConnectionContext context, String resourceKey, Map<String, Object> values) {
        JsonNode node = execute(context, "POST", resourcePath(context, resourceKey), values, Map.of());
        if (node.has("id")) {
            return node.get("id").asText();
        }
        return node.toString();
    }

    @Override
    public void write(ConnectionContext context, String resourceKey, String externalId, Map<String, Object> values) {
        execute(context, "PUT", resourcePath(context, resourceKey) + "/" + externalId, values, Map.of());
    }

    private JsonNode execute(ConnectionContext context, String method, String path,
                             Map<String, Object> body, Map<String, String> query) {
        String baseUrl = UrlSupport.trimSlash(context.getBaseUrl());
        String relative = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + relative);
        if (query != null) {
            query.forEach(builder::queryParam);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        AuthenticationType type;
        try {
            type = AuthenticationType.valueOf(context.getAuthenticationType());
        } catch (Exception e) {
            type = AuthenticationType.NONE;
        }
        authApplicator.apply(headers, type, context.getSecrets());
        try {
            RestClient client = restClientBuilder.build();
            String raw;
            if ("GET".equalsIgnoreCase(method)) {
                raw = client.get().uri(builder.toUriString())
                        .headers(h -> h.addAll(headers))
                        .retrieve()
                        .body(String.class);
            } else if ("POST".equalsIgnoreCase(method)) {
                raw = client.post().uri(builder.toUriString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> h.addAll(headers))
                        .body(body == null ? Map.of() : body)
                        .retrieve()
                        .body(String.class);
            } else {
                raw = client.put().uri(builder.toUriString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> h.addAll(headers))
                        .body(body == null ? Map.of() : body)
                        .retrieve()
                        .body(String.class);
            }
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "Custom REST"));
            }
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_UNKNOWN, Map.of("name", "Custom REST"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_UNREACHABLE, Map.of("name", "Custom REST"));
        }
    }

    private String resourcePath(ConnectionContext context, String resourceKey) {
        String prefix = setting(context, "resourcePrefix", "");
        String key = resourceKey == null ? "" : resourceKey;
        if (key.startsWith("http")) {
            return key;
        }
        String combined = (prefix == null ? "" : prefix);
        if (!combined.isBlank() && !combined.startsWith("/")) {
            combined = "/" + combined;
        }
        if (!key.isBlank()) {
            combined = combined + (key.startsWith("/") ? key : "/" + key);
        }
        return combined.isBlank() ? "/" : combined;
    }

    private List<Map<String, Object>> toList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            if (node.isArray()) {
                return objectMapper.convertValue(node, LIST_MAP);
            }
            if (node.has("items") && node.get("items").isArray()) {
                return objectMapper.convertValue(node.get("items"), LIST_MAP);
            }
            if (node.has("data") && node.get("data").isArray()) {
                return objectMapper.convertValue(node.get("data"), LIST_MAP);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private String setting(ConnectionContext context, String key, String fallback) {
        if (context.getSettings() != null && context.getSettings().get(key) != null
                && !context.getSettings().get(key).isBlank()) {
            return context.getSettings().get(key);
        }
        if (context.getSecrets() != null && context.getSecrets().get(key) != null
                && !context.getSecrets().get(key).isBlank()) {
            return context.getSecrets().get(key);
        }
        return fallback;
    }

    private String first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.get(key) != null && !String.valueOf(row.get(key)).isBlank()) {
                return String.valueOf(row.get(key));
            }
        }
        return null;
    }
}

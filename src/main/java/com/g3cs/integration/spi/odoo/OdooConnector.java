package com.g3cs.integration.spi.odoo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.service.impl.CatalogSeedServiceImpl;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.spi.ConnectionTestResult;
import com.g3cs.integration.spi.ReadPage;
import com.g3cs.integration.spi.ReadRequest;
import com.g3cs.integration.spi.RemoteConnector;
import com.g3cs.integration.spi.RemoteField;
import com.g3cs.integration.spi.RemoteResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class OdooConnector implements RemoteConnector {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP = new TypeReference<>() {};

    private final OdooJson2Client client;
    private final ObjectMapper objectMapper;

    public OdooConnector(OdooJson2Client client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String connectorId() {
        return CatalogSeedServiceImpl.CON_ODOO;
    }

    @Override
    public ConnectionTestResult test(ConnectionContext context) {
        try {
            JsonNode version = client.getVersion(context);
            String versionLabel = version.path("server_version").asText(version.path("version").asText(""));
            client.call(context, "res.users", "search_read",
                    client.searchReadBody(List.of(), List.of("id", "name"), 0, 1, null));
            return ConnectionTestResult.builder()
                    .success(true)
                    .version(versionLabel)
                    .build();
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
                    .errorMessage(MessageCode.CONNECTION_TEST_FAILED_UNKNOWN.resolve(Map.of("name", "Odoo")))
                    .build();
        }
    }

    @Override
    public List<RemoteResource> discoverResources(ConnectionContext context, String filter) {
        List<Object> domain = new ArrayList<>();
        if (filter != null && !filter.isBlank()) {
            domain.add(List.of("model", "ilike", filter.trim()));
        }
        JsonNode node = client.call(context, "ir.model", "search_read",
                client.searchReadBody(domain, List.of("model", "name"), 0, 500, "model asc"));
        List<Map<String, Object>> rows = readList(node);
        List<RemoteResource> resources = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String model = String.valueOf(row.get("model"));
            resources.add(RemoteResource.builder()
                    .key(model)
                    .model(model)
                    .label(row.get("name") == null ? model : String.valueOf(row.get("name")))
                    .build());
        }
        return resources;
    }

    @Override
    public List<RemoteField> discoverFields(ConnectionContext context, String resourceKey) {
        JsonNode node = client.call(context, resourceKey, "fields_get", Map.of(
                "attributes", List.of("string", "type", "required", "readonly", "relation", "selection")
        ));
        List<RemoteField> fields = new ArrayList<>();
        if (node == null || !node.isObject()) {
            return fields;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            JsonNode meta = entry.getValue();
            fields.add(RemoteField.builder()
                    .name(entry.getKey())
                    .label(meta.path("string").asText(entry.getKey()))
                    .type(meta.path("type").asText("char"))
                    .required(meta.path("required").asBoolean(false))
                    .readonly(meta.path("readonly").asBoolean(false))
                    .relation(meta.path("relation").asText(null))
                    .build());
        }
        return fields;
    }

    @Override
    public ReadPage searchRead(ConnectionContext context, ReadRequest request) {
        int limit = request.getLimit() == null ? 100 : request.getLimit();
        int offset = request.getOffset() == null ? 0 : request.getOffset();
        String order = request.getOrder() == null || request.getOrder().isBlank()
                ? "write_date asc, id asc" : request.getOrder();
        JsonNode node = client.call(context, request.getResourceKey(), "search_read",
                client.searchReadBody(request.getDomain(), request.getFields(), offset, limit, order));
        List<Map<String, Object>> records = readList(node);
        boolean hasMore = records.size() >= limit;
        return ReadPage.builder()
                .records(records)
                .hasMore(hasMore)
                .nextOffset(hasMore ? offset + limit : null)
                .build();
    }

    @Override
    public Object create(ConnectionContext context, String resourceKey, Map<String, Object> values) {
        JsonNode node = client.call(context, resourceKey, "create", Map.of("vals_list", List.of(values)));
        if (node.isArray() && !node.isEmpty()) {
            return node.get(0).asText();
        }
        return node.asText();
    }

    @Override
    public void write(ConnectionContext context, String resourceKey, String externalId, Map<String, Object> values) {
        int id;
        try {
            id = Integer.parseInt(externalId);
        } catch (NumberFormatException e) {
            throw new BusinessException(MessageCode.ODOO_UPSTREAM_ERROR);
        }
        client.call(context, resourceKey, "write", Map.of("ids", List.of(id), "vals", values));
    }

    private List<Map<String, Object>> readList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            if (node.isArray()) {
                return objectMapper.convertValue(node, LIST_MAP);
            }
            if (node.has("result") && node.get("result").isArray()) {
                return objectMapper.convertValue(node.get("result"), LIST_MAP);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }
}

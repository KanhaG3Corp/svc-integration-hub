package com.g3cs.integration.spi.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.spi.AuthApplicator;
import com.g3cs.integration.spi.ConnectionContext;
import com.g3cs.integration.utils.UrlSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OdooJson2Client {

    private final RestClient.Builder restClientBuilder;
    private final AuthApplicator authApplicator;
    private final ObjectMapper objectMapper;

    public OdooJson2Client(RestClient.Builder restClientBuilder,
                           AuthApplicator authApplicator,
                           ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.authApplicator = authApplicator;
        this.objectMapper = objectMapper;
    }

    public JsonNode getVersion(ConnectionContext context) {
        return execute("GET", "/web/version", context, Map.of());
    }

    public JsonNode call(ConnectionContext context, String model, String method, Map<String, Object> body) {
        return execute("POST", "/json/2/" + model + "/" + method, context, body == null ? Map.of() : body);
    }

    private JsonNode execute(String httpMethod, String path, ConnectionContext context, Map<String, Object> body) {
        String baseUrl = UrlSupport.trimSlash(context.getBaseUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        AuthenticationType type;
        try {
            type = AuthenticationType.valueOf(context.getAuthenticationType());
        } catch (Exception e) {
            type = AuthenticationType.BEARER;
        }
        authApplicator.apply(headers, type, context.getSecrets());
        if (context.getDatabase() != null && !context.getDatabase().isBlank()) {
            headers.set("X-Odoo-Database", context.getDatabase());
        }
        try {
            RestClient.RequestHeadersSpec<?> spec;
            if ("GET".equalsIgnoreCase(httpMethod)) {
                spec = restClientBuilder.build().get().uri(baseUrl + path);
            } else {
                spec = restClientBuilder.build().post().uri(baseUrl + path)
                        .body(body);
            }
            String raw = spec
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException e) {
            throw mapOdooError(e, path);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_UNREACHABLE, Map.of("name", "Odoo"));
        }
    }

    private BusinessException mapOdooError(RestClientResponseException e, String path) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "Odoo"));
        }
        if (status == 404 && path.contains("/json/2/")) {
            String model = path.replace("/json/2/", "").split("/")[0];
            return new BusinessException(MessageCode.ODOO_MODEL_NOT_FOUND, Map.of("model", model));
        }
        return new BusinessException(MessageCode.ODOO_UPSTREAM_ERROR, Map.of());
    }

    public Map<String, Object> searchReadBody(List<Object> domain, List<String> fields,
                                              Integer offset, Integer limit, String order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain", domain == null ? List.of() : domain);
        body.put("fields", fields == null ? List.of() : fields);
        if (offset != null) {
            body.put("offset", offset);
        }
        if (limit != null) {
            body.put("limit", limit);
        }
        if (order != null && !order.isBlank()) {
            body.put("order", order);
        }
        return body;
    }
}

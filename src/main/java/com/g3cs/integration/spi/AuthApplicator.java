package com.g3cs.integration.spi;

import com.g3cs.integration.common.enums.AuthenticationType;
import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class AuthApplicator {

    private final RestClient.Builder restClientBuilder;

    public AuthApplicator(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public void apply(HttpHeaders headers, AuthenticationType type, Map<String, String> secrets) {
        Map<String, String> safe = secrets == null ? Map.of() : secrets;
        switch (type == null ? AuthenticationType.NONE : type) {
            case API_KEY -> {
                String key = firstNonBlank(safe, "apiKey", "token", "accessToken");
                if (key == null) {
                    throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
                }
                headers.setBearerAuth(key);
            }
            case BEARER -> {
                String token = firstNonBlank(safe, "token", "accessToken", "apiKey");
                if (token == null) {
                    throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
                }
                headers.setBearerAuth(token);
            }
            case BASIC -> {
                String username = firstNonBlank(safe, "username", "user");
                String password = firstNonBlank(safe, "password");
                if (username == null || password == null) {
                    throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
                }
                String encoded = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            case OAUTH2 -> {
                String token = firstNonBlank(safe, "accessToken", "token");
                if (token == null) {
                    token = fetchClientCredentialsToken(safe);
                }
                headers.setBearerAuth(token);
            }
            case CUSTOM_HEADER -> {
                String headerName = firstNonBlank(safe, "headerName");
                String headerValue = firstNonBlank(safe, "headerValue");
                if (headerName == null || headerValue == null) {
                    throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
                }
                headers.set(headerName, headerValue);
            }
            case NONE -> {
                // no credentials
            }
        }
    }

    private String fetchClientCredentialsToken(Map<String, String> secrets) {
        String tokenUrl = firstNonBlank(secrets, "tokenUrl");
        String clientId = firstNonBlank(secrets, "clientId");
        String clientSecret = firstNonBlank(secrets, "clientSecret");
        if (tokenUrl == null || clientId == null || clientSecret == null) {
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
        }
        String body = "grant_type=client_credentials&client_id=" + clientId
                + "&client_secret=" + clientSecret;
        String scope = firstNonBlank(secrets, "scope");
        if (scope != null) {
            body = body + "&scope=" + scope;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("access_token") == null) {
                throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
            }
            return String.valueOf(response.get("access_token"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(MessageCode.CONNECTION_TEST_FAILED_AUTH, Map.of("name", "connection"));
        }
    }

    private String firstNonBlank(Map<String, String> secrets, String... keys) {
        for (String key : keys) {
            String value = secrets.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

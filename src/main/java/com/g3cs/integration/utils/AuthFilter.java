package com.g3cs.integration.utils;

import com.g3cs.integration.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Same JWT + session validation used by tprm-core so tenant switching stays consistent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthFilter extends OncePerRequestFilter {

    private final MongoTemplate mongoTemplate;
    private final TokenPayloadUtil tokenPayloadUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("#{'${endpoint.open_endpoints:}'.split(',')}")
    private List<String> openEndpoints;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<String> normalized = openEndpoints == null ? Collections.emptyList()
                : openEndpoints.stream()
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
        if (normalized.stream().anyMatch(path::contains)) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendError(response, 401, "Missing or invalid Authorization header");
            return;
        }

        String token = auth.substring(7);
        Map<String, Object> payload;
        try {
            payload = tokenPayloadUtil.decode(token, jwtSecret);
        } catch (Exception e) {
            sendError(response, 403, "Forbidden - invalid or expired token");
            return;
        }
        if (payload == null) {
            sendError(response, 403, "Forbidden - invalid token payload");
            return;
        }

        String userId = (String) payload.get("userId");
        String tenantId = (String) payload.get("tenantId");
        String tenantDb = (String) payload.get("tenantDb");
        if (tenantDb == null || tenantDb.isEmpty()) {
            sendError(response, 403, "Forbidden - missing tenant identification");
            return;
        }

        TenantContext.setTenantId(tenantId);
        TenantContext.setTenantDB(tenantDb);
        TenantContext.setUserId(userId);

        try {
            SessionValidationResult validationResult = validateSession(token, payload);
            if (!validationResult.valid()) {
                sendError(response, 401, "Session invalid - Please login again");
                return;
            }
            request.setAttribute("userId", userId);
            request.setAttribute("tenantId", tenantDb);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"" + message + "\",\"data\":null}");
    }

    private SessionValidationResult validateSession(String token, Map<String, Object> payload) {
        try {
            String userId = (String) payload.get("userId");
            if (userId == null) {
                return SessionValidationResult.invalid();
            }
            Query query = Query.query(Criteria.where("_id").is(new ObjectId(userId)));
            Document user = mongoTemplate.findOne(query, Document.class, "user_details");
            if (user == null) {
                return SessionValidationResult.invalid();
            }
            Document session = (Document) user.get("session");
            if (session == null) {
                return SessionValidationResult.invalid();
            }
            if (!Boolean.TRUE.equals(session.getBoolean("active"))) {
                return SessionValidationResult.invalid();
            }
            String sessionToken = session.getString("access_token");
            if (!token.equals(sessionToken)) {
                return SessionValidationResult.invalid();
            }
            Date expiresAtDate = session.getDate("expires_at");
            if (expiresAtDate != null && expiresAtDate.toInstant().isBefore(Instant.now())) {
                return SessionValidationResult.invalid();
            }
            return SessionValidationResult.validResult();
        } catch (Exception e) {
            log.warn("Session validation failed: {}", e.getMessage());
            return SessionValidationResult.invalid();
        }
    }

    private static final class SessionValidationResult {
        private final boolean valid;

        private SessionValidationResult(boolean valid) {
            this.valid = valid;
        }

        boolean valid() {
            return valid;
        }

        static SessionValidationResult validResult() {
            return new SessionValidationResult(true);
        }

        static SessionValidationResult invalid() {
            return new SessionValidationResult(false);
        }
    }
}

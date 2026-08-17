package com.g3cs.integration.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Decodes platform JWT: HS256 signature + AES-GCM encrypted claims in "enc".
 */
@Component
public class TokenPayloadUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENC_CLAIM = "enc";

    private Key getSigningKey(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive signing key", e);
        }
    }

    public Map<String, Object> decode(String token, String secret) {
        try {
            Claims jwtClaims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey(secret))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String encryptedBlob = jwtClaims.get(ENC_CLAIM, String.class);
            if (encryptedBlob == null) {
                throw new RuntimeException("Token payload is missing encryption envelope");
            }
            byte[] plainJson = AesGcmUtil.decrypt(encryptedBlob, secret);
            return MAPPER.readValue(plainJson, new TypeReference<>() {});
        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid token", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode token", e);
        }
    }
}

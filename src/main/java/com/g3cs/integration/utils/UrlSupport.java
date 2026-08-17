package com.g3cs.integration.utils;

import com.g3cs.integration.common.exception.BusinessException;
import com.g3cs.integration.common.message.MessageCode;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

public final class UrlSupport {

    private UrlSupport() {}

    public static void requireHttpsBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(MessageCode.VALIDATION_FAILED);
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (Exception e) {
            throw new BusinessException(MessageCode.VALIDATION_FAILED, Map.of());
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(loopback && "http".equals(scheme))) {
            throw new BusinessException(MessageCode.VALIDATION_FAILED);
        }
    }

    public static String trimSlash(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static String normalizeWebsite(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String value = website.trim().toLowerCase(Locale.ROOT);
        value = value.replaceFirst("^https?://", "");
        value = value.replaceFirst("^www\\.", "");
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}

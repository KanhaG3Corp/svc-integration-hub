package com.g3cs.integration.utils;

/**
 * Masks secrets before they are written to logs.
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}

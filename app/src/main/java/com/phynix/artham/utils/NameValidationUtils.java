package com.phynix.artham.utils;

/**
 * NameValidationUtils — Utility class for sanitizing and validating
 * strings used as keys or names in local and cloud systems.
 *
 * Replaces FirebasePathUtils to avoid Firebase terminology.
 */
public final class NameValidationUtils {

    /** Characters that are illegal in names/keys for database compatibility. */
    private static final String ILLEGAL_CHARS = ".#$[]/";

    private NameValidationUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Check if the given string contains any illegal characters.
     *
     * @param key the string to validate
     * @return true if the key contains illegal characters
     */
    public static boolean containsIllegalChars(String key) {
        if (key == null || key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            if (ILLEGAL_CHARS.indexOf(key.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitize the given string by removing all illegal characters.
     *
     * @param key the raw string
     * @return a sanitized version safe for use
     */
    public static String sanitize(String key) {
        if (key == null) return "";
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (ILLEGAL_CHARS.indexOf(c) < 0) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Returns a user-friendly error message listing the illegal characters.
     */
    public static String getIllegalCharsMessage() {
        return "Name cannot contain these characters: . # $ [ ] /";
    }
}

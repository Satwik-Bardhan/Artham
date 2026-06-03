package com.phynix.artham.utils;

/**
 * FirebasePathUtils — Utility class for sanitizing and validating
 * strings used as Firebase Realtime Database path keys.
 *
 * Firebase Database keys CANNOT contain: . # $ [ ] /
 * Using any of these characters throws a DatabaseException at runtime.
 */
public final class FirebasePathUtils {

    /** Characters that are illegal in Firebase Realtime Database keys. */
    private static final String ILLEGAL_CHARS = ".#$[]/";

    private FirebasePathUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Check if the given string contains any characters that are
     * illegal in a Firebase Database path key.
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
     * Sanitize the given string by removing all characters that are
     * illegal in Firebase Database path keys.
     *
     * @param key the raw string
     * @return a sanitized version safe for use as a Firebase key
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

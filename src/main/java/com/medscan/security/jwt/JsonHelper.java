package com.medscan.security.jwt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight, zero-dependency JSON utility for JWT payload serialization and parsing.
 * Eliminates third-party library dependencies and runtime classpath conflicts.
 */
public final class JsonHelper {

    private JsonHelper() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extracts a top-level string property value from a JSON string.
     */
    public static String getString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex == -1) {
            return null;
        }

        int quoteStart = json.indexOf('"', colonIndex);
        if (quoteStart == -1) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Extracts a top-level long numeric property value from a JSON string.
     */
    public static Long getLong(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) {
            return null;
        }

        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex == -1) {
            return null;
        }

        int i = colonIndex + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            i++;
        }

        if (start == i) {
            return null;
        }

        try {
            return Long.parseLong(json.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts a string array from either a top-level key or inside a nested object.
     * E.g. key "roles" or "permissions".
     */
    public static Set<String> getStringArray(String json, String arrayKey) {
        String pattern = "\"" + arrayKey + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) {
            return Collections.emptySet();
        }

        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex == -1) {
            return Collections.emptySet();
        }

        int bracketStart = json.indexOf('[', colonIndex);
        if (bracketStart == -1) {
            return Collections.emptySet();
        }

        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketEnd == -1) {
            return Collections.emptySet();
        }

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        Set<String> result = new HashSet<>();
        boolean insideString = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '"') {
                if (insideString) {
                    result.add(current.toString());
                    current.setLength(0);
                    insideString = false;
                } else {
                    insideString = true;
                }
            } else if (insideString) {
                current.append(c);
            }
        }

        return result;
    }
}

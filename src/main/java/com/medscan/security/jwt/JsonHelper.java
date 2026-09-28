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

    public static Integer getInt(String json, String key) {
        Long val = getLong(json, key);
        return val != null ? val.intValue() : null;
    }

    public static Double getDouble(String json, String key) {
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
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-' || json.charAt(i) == '.')) {
            i++;
        }

        if (start == i) {
            return null;
        }

        try {
            return Double.parseDouble(json.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> getStringList(String json, String arrayKey) {
        return new ArrayList<>(getStringArray(json, arrayKey));
    }

    /**
     * Extrait un objet JSON clé-valeur en tant que Map<String, String>.
     * Utile pour les champs personnalisés dynamiques (customFields).
     */
    public static Map<String, String> getStringMap(String json, String objectKey) {
        if (json == null || objectKey == null) {
            return Collections.emptyMap();
        }
        String pattern = "\"" + objectKey + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) {
            return Collections.emptyMap();
        }

        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex == -1) {
            return Collections.emptyMap();
        }

        int openBrace = json.indexOf('{', colonIndex);
        if (openBrace == -1) {
            return Collections.emptyMap();
        }

        int depth = 0;
        int closeBrace = -1;
        boolean inQuotes = false;
        for (int i = openBrace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        closeBrace = i;
                        break;
                    }
                }
            }
        }
        if (closeBrace == -1) {
            return Collections.emptyMap();
        }

        String content = json.substring(openBrace + 1, closeBrace);
        Map<String, String> result = new java.util.LinkedHashMap<>();

        int idx = 0;
        while (idx < content.length()) {
            int kStart = content.indexOf('"', idx);
            if (kStart == -1) break;
            int kEnd = content.indexOf('"', kStart + 1);
            if (kEnd == -1) break;
            String k = content.substring(kStart + 1, kEnd);

            int col = content.indexOf(':', kEnd + 1);
            if (col == -1) break;

            int valStart = col + 1;
            while (valStart < content.length() && Character.isWhitespace(content.charAt(valStart))) {
                valStart++;
            }
            if (valStart >= content.length()) break;

            String v;
            if (content.charAt(valStart) == '"') {
                int vEnd = content.indexOf('"', valStart + 1);
                if (vEnd == -1) break;
                v = content.substring(valStart + 1, vEnd);
                idx = vEnd + 1;
            } else {
                int comma = content.indexOf(',', valStart);
                if (comma == -1) {
                    comma = content.length();
                }
                v = content.substring(valStart, comma).trim();
                idx = comma + 1;
            }
            result.put(k, v);
        }
        return result;
    }
}

/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord;

import java.util.*;

/**
 * High-performance, zero-dependency JSON builder and lightweight extractor
 * specifically tailored for constructing Discord REST API v10 payloads.
 */
public class JsonObjectBuilder {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    public JsonObjectBuilder add(String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
        return this;
    }

    public JsonObjectBuilder add(String key, Number value) {
        if (value != null) {
            fields.put(key, value);
        }
        return this;
    }

    public JsonObjectBuilder add(String key, Boolean value) {
        if (value != null) {
            fields.put(key, value);
        }
        return this;
    }

    public JsonObjectBuilder add(String key, JsonObjectBuilder nestedObject) {
        if (nestedObject != null) {
            fields.put(key, nestedObject);
        }
        return this;
    }

    public JsonObjectBuilder add(String key, List<?> list) {
        if (list != null) {
            fields.put(key, list);
        }
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String valueToJson(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String) {
            return "\"" + escapeJson((String) val) + "\"";
        }
        if (val instanceof Number || val instanceof Boolean) {
            return val.toString();
        }
        if (val instanceof JsonObjectBuilder) {
            return ((JsonObjectBuilder) val).build();
        }
        if (val instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            List<?> list = (List<?>) val;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(valueToJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (val instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            Map<?, ?> map = (Map<?, ?>) val;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                sb.append(valueToJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escapeJson(val.toString()) + "\"";
    }

    public static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Fast, lightweight JSON string extractor for simple keys (e.g. "id", "guild_id", "code", "message").
     * Avoids needing external JSON parser dependencies.
     *
     * Supports dotted paths like "data.custom_id" — only works for object
     * nesting, not array indexing.
     */
    public static String extractStringField(String json, String fieldName) {
        if (json == null || fieldName == null) return null;

        // Dotted path: navigate nested objects
        if (fieldName.contains(".")) {
            String[] parts = fieldName.split("\\.", 2);
            String childJson = extractObjectValue(json, parts[0]);
            if (childJson == null) return null;
            return extractStringField(childJson, parts[1]);
        }

        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        // Skip whitespace after the colon
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        // If the value isn't a quoted string (e.g., number, bool, null, object, array),
        // bail out — caller should use a different extractor.
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') return null;

        int startQuote = valueStart;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escaped = false;
            } else {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Returns the substring of `json` corresponding to the value of the object
     * at top-level key `key` (i.e. "{ ... }"). Returns null if not found.
     */
    private static String extractObjectValue(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        if (start >= json.length() || json.charAt(start) != '{') return null;

        // Match braces
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * Fast JSON integer extractor for response codes (e.g., Discord error codes).
     */
    public static Integer extractIntField(String json, String fieldName) {
        if (json == null || fieldName == null) return null;
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }

        if (start < end) {
            try {
                return Integer.parseInt(json.substring(start, end));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}

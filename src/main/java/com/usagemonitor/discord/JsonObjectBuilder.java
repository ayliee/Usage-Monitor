/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Hand-rolled JSON builder because the plugin is zero-dependency.
// LinkedHashMap preserves insertion order so the output is stable for diffing.
public class JsonObjectBuilder {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    public JsonObjectBuilder add(String key, String value) {
        if (value != null) fields.put(key, value);
        return this;
    }

    public JsonObjectBuilder add(String key, Number value) {
        if (value != null) fields.put(key, value);
        return this;
    }

    public JsonObjectBuilder add(String key, Boolean value) {
        if (value != null) fields.put(key, value);
        return this;
    }

    public JsonObjectBuilder add(String key, JsonObjectBuilder nested) {
        if (nested != null) fields.put(key, nested);
        return this;
    }

    public JsonObjectBuilder add(String key, List<?> list) {
        if (list != null) fields.put(key, list);
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(esc(e.getKey())).append("\":").append(toJson(e.getValue()));
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String toJson(Object val) {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + esc((String) val) + "\"";
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        if (val instanceof JsonObjectBuilder) return ((JsonObjectBuilder) val).build();
        if (val instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) val;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(list.get(i)));
            }
            sb.append(']');
            return sb.toString();
        }
        if (val instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> m = (Map<?, ?>) val;
            boolean first = true;
            for (Map.Entry<?, ?> en : m.entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(esc(String.valueOf(en.getKey()))).append("\":").append(toJson(en.getValue()));
                first = false;
            }
            sb.append('}');
            return sb.toString();
        }
        return "\"" + esc(val.toString()) + "\"";
    }

    public static String esc(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    // Quick field extractor that handles dotted paths (e.g. "data.custom_id")
    // for the handful of fields we read back from Discord responses.
    public static String extractStringField(String json, String fieldName) {
        if (json == null || fieldName == null) return null;

        if (fieldName.contains(".")) {
            String[] parts = fieldName.split("\\.", 2);
            String child = extractObjectValue(json, parts[0]);
            if (child == null) return null;
            return extractStringField(child, parts[1]);
        }

        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;

        int v = colon + 1;
        while (v < json.length() && Character.isWhitespace(json.charAt(v))) v++;
        if (v >= json.length() || json.charAt(v) != '"') return null;

        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = v + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else if (c == '"') return sb.toString();
                else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Integer extractIntField(String json, String fieldName) {
        if (json == null || fieldName == null) return null;
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (start < end) {
            try { return Integer.parseInt(json.substring(start, end)); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String extractObjectValue(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '{') return null;
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
}

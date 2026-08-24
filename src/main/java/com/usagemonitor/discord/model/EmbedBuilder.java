/**
 * Copyright (c) 2026 AeroX Dev
 * Code by Ayle (@alyfinnn)
 * MIT License
 */
package com.usagemonitor.discord.model;

import com.usagemonitor.discord.JsonObjectBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EmbedBuilder {

    private String title;
    private String description;
    private String url;
    private Integer color;
    private String timestamp;
    private Footer footer;
    private Author author;
    private final List<Field> fields = new ArrayList<>();

    public static class Field {
        final String name;
        final String value;
        final boolean inline;
        public Field(String n, String v, boolean in) { this.name = n; this.value = v; this.inline = in; }
        public JsonObjectBuilder toJsonObject() {
            return new JsonObjectBuilder().add("name", name).add("value", value).add("inline", inline);
        }
    }

    public static class Footer {
        final String text;
        final String iconUrl;
        public Footer(String t, String i) { this.text = t; this.iconUrl = i; }
        public JsonObjectBuilder toJsonObject() {
            JsonObjectBuilder b = new JsonObjectBuilder().add("text", text);
            if (iconUrl != null && !iconUrl.isEmpty()) b.add("icon_url", iconUrl);
            return b;
        }
    }

    public static class Author {
        final String name;
        final String url;
        final String iconUrl;
        public Author(String n, String u, String i) { this.name = n; this.url = u; this.iconUrl = i; }
        public JsonObjectBuilder toJsonObject() {
            JsonObjectBuilder b = new JsonObjectBuilder().add("name", name);
            if (url != null && !url.isEmpty()) b.add("url", url);
            if (iconUrl != null && !iconUrl.isEmpty()) b.add("icon_url", iconUrl);
            return b;
        }
    }

    public EmbedBuilder setTitle(String t) { this.title = t; return this; }
    public EmbedBuilder setDescription(String d) { this.description = d; return this; }
    public EmbedBuilder setUrl(String u) { this.url = u; return this; }
    public EmbedBuilder setColor(int c) { this.color = c; return this; }

    public EmbedBuilder setColorHex(String hex) {
        if (hex != null && !hex.isEmpty()) {
            try { this.color = Integer.parseInt(hex.replace("#", "").trim(), 16); }
            catch (NumberFormatException ignored) {}
        }
        return this;
    }

    public EmbedBuilder setTimestamp(Instant t) { if (t != null) this.timestamp = t.toString(); return this; }

    public EmbedBuilder setFooter(String text, String iconUrl) {
        this.footer = new Footer(text, iconUrl);
        return this;
    }

    public EmbedBuilder setAuthor(String name, String url, String iconUrl) {
        this.author = new Author(name, url, iconUrl);
        return this;
    }

    public EmbedBuilder addField(String name, String value, boolean inline) {
        fields.add(new Field(name, value, inline));
        return this;
    }

    public JsonObjectBuilder toJsonObject() {
        JsonObjectBuilder b = new JsonObjectBuilder();
        if (title != null) b.add("title", title);
        if (description != null) b.add("description", description);
        if (url != null) b.add("url", url);
        if (color != null) b.add("color", color);
        if (timestamp != null) b.add("timestamp", timestamp);
        if (footer != null) b.add("footer", footer.toJsonObject());
        if (author != null) b.add("author", author.toJsonObject());
        if (!fields.isEmpty()) {
            List<JsonObjectBuilder> arr = new ArrayList<>();
            for (Field f : fields) arr.add(f.toJsonObject());
            b.add("fields", arr);
        }
        return b;
    }
}

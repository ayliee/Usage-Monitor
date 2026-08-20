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

/**
 * Fluent builder for Discord Rich Embeds according to Discord REST API v10 specs.
 */
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
        private final String name;
        private final String value;
        private final boolean inline;

        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }

        public JsonObjectBuilder toJsonObject() {
            return new JsonObjectBuilder()
                    .add("name", name)
                    .add("value", value)
                    .add("inline", inline);
        }
    }

    public static class Footer {
        private final String text;
        private final String iconUrl;

        public Footer(String text, String iconUrl) {
            this.text = text;
            this.iconUrl = iconUrl;
        }

        public JsonObjectBuilder toJsonObject() {
            JsonObjectBuilder builder = new JsonObjectBuilder().add("text", text);
            if (iconUrl != null && !iconUrl.isEmpty()) {
                builder.add("icon_url", iconUrl);
            }
            return builder;
        }
    }

    public static class Author {
        private final String name;
        private final String url;
        private final String iconUrl;

        public Author(String name, String url, String iconUrl) {
            this.name = name;
            this.url = url;
            this.iconUrl = iconUrl;
        }

        public JsonObjectBuilder toJsonObject() {
            JsonObjectBuilder builder = new JsonObjectBuilder().add("name", name);
            if (url != null && !url.isEmpty()) {
                builder.add("url", url);
            }
            if (iconUrl != null && !iconUrl.isEmpty()) {
                builder.add("icon_url", iconUrl);
            }
            return builder;
        }
    }

    public EmbedBuilder setTitle(String title) {
        this.title = title;
        return this;
    }

    public EmbedBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public EmbedBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public EmbedBuilder setColor(int color) {
        this.color = color;
        return this;
    }

    public EmbedBuilder setColorHex(String hexColor) {
        if (hexColor != null && !hexColor.isEmpty()) {
            String cleanHex = hexColor.replace("#", "").trim();
            try {
                this.color = Integer.parseInt(cleanHex, 16);
            } catch (NumberFormatException ignored) {}
        }
        return this;
    }

    public EmbedBuilder setTimestamp(Instant instant) {
        if (instant != null) {
            this.timestamp = instant.toString();
        }
        return this;
    }

    public EmbedBuilder setFooter(String text, String iconUrl) {
        this.footer = new Footer(text, iconUrl);
        return this;
    }

    public EmbedBuilder setAuthor(String name, String url, String iconUrl) {
        this.author = new Author(name, url, iconUrl);
        return this;
    }

    public EmbedBuilder addField(String name, String value, boolean inline) {
        this.fields.add(new Field(name, value, inline));
        return this;
    }

    public JsonObjectBuilder toJsonObject() {
        JsonObjectBuilder builder = new JsonObjectBuilder();
        if (title != null) builder.add("title", title);
        if (description != null) builder.add("description", description);
        if (url != null) builder.add("url", url);
        if (color != null) builder.add("color", color);
        if (timestamp != null) builder.add("timestamp", timestamp);
        if (footer != null) builder.add("footer", footer.toJsonObject());
        if (author != null) builder.add("author", author.toJsonObject());

        if (!fields.isEmpty()) {
            List<JsonObjectBuilder> fieldObjects = new ArrayList<>();
            for (Field f : fields) {
                fieldObjects.add(f.toJsonObject());
            }
            builder.add("fields", fieldObjects);
        }

        return builder;
    }
}

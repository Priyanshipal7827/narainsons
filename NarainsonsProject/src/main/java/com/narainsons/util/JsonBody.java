package com.narainsons.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import jakarta.servlet.http.HttpServletRequest;

public final class JsonBody {

    private JsonBody() {
    }

    public static JsonObject readObject(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            throw new IllegalArgumentException(
                    "Body type form-data hai — Postman mein Body → raw → JSON choose karo, form-data mat use karo");
        }

        if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
            JsonObject json = new JsonObject();
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    json.addProperty(entry.getKey(), values[0]);
                }
            }
            if (json.size() == 0) {
                throw new IllegalArgumentException("Form body khali hai — fields ya JSON bhejo");
            }
            return json;
        }

        String raw = readRaw(request);
        return parseObject(raw);
    }

    public static String readRaw(HttpServletRequest request) throws IOException {
        return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    public static JsonObject parseObject(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException(
                    "Request body khali hai. Postman: Body → raw → JSON, Header: Content-Type = application/json");
        }
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    "JSON invalid hai. Copy karo: {\"name\":\"Test\",\"mobile_number\":\"9876543210\",\"password\":\"Test@123\"}");
        }
    }

    public static String string(JsonObject json, String snakeKey, String camelKey) {
        if (json.has(snakeKey) && !json.get(snakeKey).isJsonNull()) {
            return primitiveText(json, snakeKey);
        }
        if (camelKey != null && json.has(camelKey) && !json.get(camelKey).isJsonNull()) {
            return primitiveText(json, camelKey);
        }
        return null;
    }

    private static String primitiveText(JsonObject json, String key) {
        if (!json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException(key + " must be a single value");
        }
        return json.get(key).getAsString();
    }

    public static Long longValue(JsonObject json, String snakeKey, String camelKey) {
        String text = string(json, snakeKey, camelKey);
        if (text == null || text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }
}

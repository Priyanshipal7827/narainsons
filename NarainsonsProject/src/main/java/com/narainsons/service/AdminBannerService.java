package com.narainsons.service;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.narainsons.dao.BannerDAO;
import com.narainsons.model.DashboardBanner;
import com.narainsons.util.ApiException;
import com.narainsons.util.BannerImageStore;
import com.narainsons.util.Validators;
import com.narainsons.util.JsonBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

public class AdminBannerService {

    private static final Set<String> ACTION_TYPES = Set.of("none", "url", "screen");
    private static final DateTimeFormatter DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BannerDAO bannerDAO = new BannerDAO();

    public Map<String, Object> listDashboardBanners(int limit) throws SQLException {
        int capped = Math.min(Math.max(limit, 1), 500);
        List<DashboardBanner> banners = bannerDAO.listAllForAdmin(capped);
        Map<String, Object> data = new HashMap<>();
        data.put("source", Map.of("database", "narainson_db", "table", "dashboard_banners"));
        data.put("stats", Map.of(
                "total", bannerDAO.countAll(),
                "active", bannerDAO.countActive(),
                "inactive", bannerDAO.countAll() - bannerDAO.countActive()));
        data.put("limit", capped);
        data.put("banners", banners.stream().map(this::toAdminMap).collect(Collectors.toList()));
        return data;
    }

    public Map<String, Object> getBanner(long id) throws SQLException {
        DashboardBanner b = bannerDAO.findById(id)
                .orElseThrow(() -> ApiException.notFound("Banner not found (id=" + id + ")"));
        return Map.of("banner", toAdminMap(b));
    }

    public Map<String, Object> createBanner(JsonObject json) throws SQLException {
        DashboardBanner b = fromJson(json, null);
        validateForWrite(b, true);
        long id = bannerDAO.insert(b);
        DashboardBanner saved = bannerDAO.findById(id)
                .orElseThrow(() -> new SQLException("Banner created but reload failed"));
        return Map.of("banner", toAdminMap(saved));
    }

    public Map<String, Object> updateBanner(long id, JsonObject json) throws SQLException {
        DashboardBanner existing = bannerDAO.findById(id)
                .orElseThrow(() -> ApiException.notFound("Banner not found (id=" + id + ")"));
        DashboardBanner b = fromJson(json, existing);
        b.setId(id);
        validateForWrite(b, false);
        if (!bannerDAO.update(b)) {
            throw new IllegalArgumentException("Banner update failed (id=" + id + ")");
        }
        DashboardBanner saved = bannerDAO.findById(id)
                .orElseThrow(() -> new SQLException("Banner updated but reload failed"));
        return Map.of("banner", toAdminMap(saved));
    }

    public Map<String, Object> uploadBannerImage(Part part, HttpServletRequest request) throws IOException {
        if (part == null || part.getSize() <= 0) {
            throw new IllegalArgumentException("Choose an image file to upload");
        }
        BannerImageStore.validateUpload(part.getContentType(), part.getSize());
        String fileName = BannerImageStore.save(
                part.getInputStream(),
                part.getContentType(),
                part.getSubmittedFileName());
        String imageUrl = BannerImageStore.publicUrl(request, fileName);
        Map<String, Object> data = new HashMap<>();
        data.put("file_name", fileName);
        data.put("image_url", imageUrl);
        return data;
    }

    public Map<String, Object> deleteBanner(long id) throws SQLException {
        if (bannerDAO.findById(id).isEmpty()) {
            throw ApiException.notFound("Banner not found (id=" + id + ")");
        }
        if (!bannerDAO.deleteById(id)) {
            throw new IllegalArgumentException("Banner delete failed (id=" + id + ")");
        }
        return Map.of("deleted", true, "id", id);
    }

    private DashboardBanner fromJson(JsonObject json, DashboardBanner base) {
        DashboardBanner b = new DashboardBanner();
        if (base != null) {
            b.setId(base.getId());
            b.setTitle(base.getTitle());
            b.setSubtitle(base.getSubtitle());
            b.setImageUrl(base.getImageUrl());
            b.setActionType(base.getActionType());
            b.setActionValue(base.getActionValue());
            b.setSortOrder(base.getSortOrder());
            b.setActive(base.getActive());
            b.setStartsAt(base.getStartsAt());
            b.setEndsAt(base.getEndsAt());
        } else {
            b.setActionType("none");
            b.setSortOrder(0);
            b.setActive(true);
        }

        if (json.has("title") && !json.get("title").isJsonNull()) {
            b.setTitle(JsonBody.string(json, "title", null));
        }
        if (json.has("subtitle")) {
            b.setSubtitle(json.get("subtitle").isJsonNull() ? null : JsonBody.string(json, "subtitle", null));
        }
        if (json.has("image_url") || json.has("imageUrl")) {
            b.setImageUrl(JsonBody.string(json, "image_url", "imageUrl"));
        }
        if (json.has("action_type") || json.has("actionType")) {
            b.setActionType(normalizeActionType(JsonBody.string(json, "action_type", "actionType")));
        }
        if (json.has("action_value") || json.has("actionValue")) {
            JsonElement av = json.has("action_value") ? json.get("action_value") : json.get("actionValue");
            b.setActionValue(av == null || av.isJsonNull() ? null : av.getAsString());
        }
        if (json.has("sort_order") || json.has("sortOrder")) {
            b.setSortOrder(intFromJson(json, "sort_order", "sortOrder", b.getSortOrder() != null ? b.getSortOrder() : 0));
        }
        if (json.has("is_active") || json.has("isActive") || json.has("active")) {
            b.setActive(boolFromJson(json, "is_active", "isActive", "active", b.getActive()));
        }
        if (json.has("starts_at") || json.has("startsAt")) {
            b.setStartsAt(parseDateTime(json, "starts_at", "startsAt"));
        }
        if (json.has("ends_at") || json.has("endsAt")) {
            b.setEndsAt(parseDateTime(json, "ends_at", "endsAt"));
        }
        return b;
    }

    private void validateForWrite(DashboardBanner b, boolean creating) {
        if (creating || b.getTitle() != null) {
            if (b.getTitle() == null || b.getTitle().isBlank()) {
                throw new IllegalArgumentException("title required (max 200 chars)");
            }
            if (b.getTitle().length() > 200) {
                throw new IllegalArgumentException("title max 200 characters");
            }
        }
        if (creating || b.getImageUrl() != null) {
            if (b.getImageUrl() == null || b.getImageUrl().isBlank()) {
                throw new IllegalArgumentException("image_url required");
            }
            if (b.getImageUrl().length() > 1000) {
                throw new IllegalArgumentException("image_url max 1000 characters");
            }
            if (!Validators.isSafeUrl(b.getImageUrl())) {
                throw new IllegalArgumentException("image_url must be an http or https URL");
            }
        }
        if (b.getSubtitle() != null && b.getSubtitle().length() > 500) {
            throw new IllegalArgumentException("subtitle max 500 characters");
        }
        String action = normalizeActionType(b.getActionType());
        b.setActionType(action);
        if (!ACTION_TYPES.contains(action)) {
            throw new IllegalArgumentException("action_type must be none, url, or screen");
        }
        if (("url".equals(action) || "screen".equals(action))
                && (b.getActionValue() == null || b.getActionValue().isBlank())) {
            throw new IllegalArgumentException("action_value required when action_type is " + action);
        }
        if ("url".equals(action) && !Validators.isSafeUrl(b.getActionValue())) {
            throw new IllegalArgumentException("action_value must be an http or https URL");
        }
        if (b.getActionValue() != null && b.getActionValue().length() > 500) {
            throw new IllegalArgumentException("action_value max 500 characters");
        }
        if (b.getStartsAt() != null && b.getEndsAt() != null && b.getEndsAt().isBefore(b.getStartsAt())) {
            throw new IllegalArgumentException("ends_at must be after starts_at");
        }
    }

    private static String normalizeActionType(String type) {
        if (type == null || type.isBlank()) {
            return "none";
        }
        return type.trim().toLowerCase();
    }

    private static int intFromJson(JsonObject json, String snake, String camel, int fallback) {
        JsonElement el = json.has(snake) ? json.get(snake) : (json.has(camel) ? json.get(camel) : null);
        if (el == null || el.isJsonNull()) {
            return fallback;
        }
        return el.getAsInt();
    }

    private static boolean boolFromJson(JsonObject json, String snake, String camel, String alt, Boolean fallback) {
        JsonElement el = null;
        if (json.has(snake)) {
            el = json.get(snake);
        } else if (json.has(camel)) {
            el = json.get(camel);
        } else if (alt != null && json.has(alt)) {
            el = json.get(alt);
        }
        if (el == null || el.isJsonNull()) {
            return Boolean.TRUE.equals(fallback);
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsBoolean();
        }
        String s = el.getAsString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }

    private static LocalDateTime parseDateTime(JsonObject json, String snake, String camel) {
        if (json.has(snake) && json.get(snake).isJsonNull()) {
            return null;
        }
        if (json.has(camel) && json.get(camel).isJsonNull() && !json.has(snake)) {
            return null;
        }
        String raw = JsonBody.string(json, snake, camel);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim().replace(' ', 'T');
        if (raw.length() == 16) {
            raw = raw + ":00";
        }
        try {
            return LocalDateTime.parse(raw, DT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime: " + raw + " (use yyyy-MM-ddTHH:mm:ss)");
        }
    }

    private Map<String, Object> toAdminMap(DashboardBanner b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("subtitle", b.getSubtitle());
        m.put("image_url", b.getImageUrl());
        m.put("action_type", b.getActionType());
        m.put("action_value", b.getActionValue());
        m.put("sort_order", b.getSortOrder());
        m.put("is_active", b.getActive());
        m.put("starts_at", b.getStartsAt());
        m.put("ends_at", b.getEndsAt());
        m.put("created_at", b.getCreatedAt());
        m.put("updated_at", b.getUpdatedAt());
        return m;
    }
}

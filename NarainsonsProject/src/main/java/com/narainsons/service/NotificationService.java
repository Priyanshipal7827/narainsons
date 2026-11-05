package com.narainsons.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonParser;
import com.narainsons.dao.NotificationDAO;
import com.narainsons.util.GsonProvider;

public class NotificationService {

    private final NotificationDAO dao = new NotificationDAO();

    public void notifyUser(long userId, String category, String title, String body, Map<String, Object> payload)
            throws SQLException {
        String json = payload == null || payload.isEmpty() ? null : GsonProvider.toJson(payload);
        long id = dao.insert(userId, category, title, body, json);
        // data values must be strings in FCM; the app opens the right screen from these.
        Map<String, String> data = new HashMap<>();
        if (payload != null) {
            payload.forEach((k, v) -> data.put(k, String.valueOf(v)));
        }
        data.put("notification_id", String.valueOf(id));
        data.put("category", category);
        PushService.shared().sendToUser(userId, title, body, data);
    }

    public Map<String, Object> listForUser(long userId, int limit, int offset) throws SQLException {
        List<NotificationDAO.NotificationRow> rows = dao.listForUser(userId, limit, offset);
        Map<String, Object> data = new HashMap<>();
        data.put("unread_count", dao.countUnread(userId));
        data.put("notifications", rows.stream().map(this::payload).collect(Collectors.toList()));
        return data;
    }

    public void markAllRead(long userId) throws SQLException {
        dao.markAllRead(userId);
    }

    public void markRead(long userId, long notificationId) throws SQLException {
        dao.markRead(userId, notificationId);
    }

    private Map<String, Object> payload(NotificationDAO.NotificationRow n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.id());
        m.put("category", n.category());
        m.put("title", n.title());
        m.put("body", n.body());
        m.put("payload", n.payloadJson() == null ? null : JsonParser.parseString(n.payloadJson()));
        m.put("read", n.readAt() != null);
        m.put("created_at", n.createdAt());
        return m;
    }
}

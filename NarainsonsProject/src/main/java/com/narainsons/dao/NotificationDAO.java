package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.narainsons.util.DBConnection;

public class NotificationDAO {

    public record NotificationRow(
            long id, long userId, String category, String title, String body,
            String payloadJson, LocalDateTime readAt, LocalDateTime createdAt) {
    }

    public long insert(long userId, String category, String title, String body, String payloadJson)
            throws SQLException {
        String sql = """
                INSERT INTO user_notifications (user_id, category, title, body, payload_json)
                VALUES (?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, category);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, payloadJson);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Notification insert failed");
    }

    public List<NotificationRow> listForUser(long userId, int limit, int offset) throws SQLException {
        String sql = """
                SELECT id, user_id, category, title, body, payload_json, read_at, created_at
                FROM user_notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """;
        List<NotificationRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public int countUnread(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND read_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void markRead(long userId, long notificationId) throws SQLException {
        String sql = "UPDATE user_notifications SET read_at=NOW() WHERE id=? AND user_id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void markAllRead(long userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE user_notifications SET read_at=NOW() WHERE user_id=? AND read_at IS NULL")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    private static NotificationRow map(ResultSet rs) throws SQLException {
        Timestamp read = rs.getTimestamp("read_at");
        Timestamp created = rs.getTimestamp("created_at");
        return new NotificationRow(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("category"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("payload_json"),
                read == null ? null : read.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime());
    }
}

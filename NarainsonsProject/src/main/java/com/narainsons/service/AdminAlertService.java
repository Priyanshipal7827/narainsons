package com.narainsons.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.narainsons.util.DBConnection;

/** Alerts for staff: the bell, the alert sound and the Notifications page all read from here. */
public class AdminAlertService {

    private static final Logger LOG = Logger.getLogger(AdminAlertService.class.getName());
    /** Never fails the customer's request: a missed alert must not lose a loan application. */
    public void raise(String type, String title, String body, String link) {
        String sql = "INSERT INTO admin_notifications (type, title, body, link) VALUES (?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, cut(title, 200));
            ps.setString(3, cut(body, 500));
            ps.setString(4, link);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not save admin alert", e);
        }
    }

    /** New items are those above afterId; afterId 0 means this browser has not polled yet. */
    public Map<String, Object> poll(long adminId, long afterId) throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();
        long readId = readMarker(adminId);
        long latest = 0;
        long unread = 0;
        String stats = "SELECT COALESCE(MAX(id),0), COALESCE(SUM(id > ?),0) FROM admin_notifications";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(stats)) {
            ps.setLong(1, readId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                latest = rs.getLong(1);
                unread = rs.getLong(2);
            }
        }
        data.put("latest_id", latest);
        data.put("unread_count", unread);
        data.put("recent", query("SELECT * FROM admin_notifications ORDER BY id DESC LIMIT 15", -1));
        data.put("new_items", afterId > 0 && latest > afterId
                ? query("SELECT * FROM admin_notifications WHERE id > ? ORDER BY id ASC LIMIT 20", afterId)
                : List.of());
        return data;
    }

    public void markAllRead(long adminId) throws SQLException {
        String sql = "UPDATE admins SET alerts_read_id = (SELECT COALESCE(MAX(id),0) FROM admin_notifications) WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.executeUpdate();
        }
    }

    private static long readMarker(long adminId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT alerts_read_id FROM admins WHERE id=?")) {
            ps.setLong(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static List<Map<String, Object>> query(String sql, long afterId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            if (afterId >= 0) {
                ps.setLong(1, afterId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("type", rs.getString("type"));
                    m.put("title", rs.getString("title"));
                    m.put("body", rs.getString("body"));
                    m.put("link", rs.getString("link"));
                    java.sql.Timestamp ts = rs.getTimestamp("created_at");
                    m.put("created_at", ts == null ? null : ts.toLocalDateTime().toString().replace('T', ' ').substring(0, 16));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}

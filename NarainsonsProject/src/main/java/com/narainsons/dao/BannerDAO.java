package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.narainsons.model.DashboardBanner;
import com.narainsons.util.DBConnection;

public class BannerDAO {

    public long countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM dashboard_banners";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public long countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM dashboard_banners WHERE is_active = 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** All rows for admin (includes inactive / scheduled). */
    public List<DashboardBanner> listAllForAdmin(int limit) throws SQLException {
        String sql = """
                SELECT id, title, subtitle, image_url, action_type, action_value, sort_order,
                       is_active, starts_at, ends_at, created_at, updated_at
                FROM dashboard_banners
                ORDER BY sort_order ASC, id ASC
                LIMIT ?
                """;
        List<DashboardBanner> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowAdmin(rs));
                }
            }
        }
        return list;
    }

    public Optional<DashboardBanner> findById(long id) throws SQLException {
        String sql = """
                SELECT id, title, subtitle, image_url, action_type, action_value, sort_order,
                       is_active, starts_at, ends_at, created_at, updated_at
                FROM dashboard_banners
                WHERE id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowAdmin(rs));
                }
            }
        }
        return Optional.empty();
    }

    public long insert(DashboardBanner b) throws SQLException {
        String sql = """
                INSERT INTO dashboard_banners
                    (title, subtitle, image_url, action_type, action_value, sort_order, is_active, starts_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bindWritable(ps, b);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Insert banner failed — no generated id");
    }

    public boolean update(DashboardBanner b) throws SQLException {
        String sql = """
                UPDATE dashboard_banners
                SET title = ?, subtitle = ?, image_url = ?, action_type = ?, action_value = ?,
                    sort_order = ?, is_active = ?, starts_at = ?, ends_at = ?
                WHERE id = ?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWritable(ps, b);
            ps.setLong(10, b.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteById(long id) throws SQLException {
        String sql = "DELETE FROM dashboard_banners WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public List<DashboardBanner> findActiveForDashboard() throws SQLException {
        String sql = """
                SELECT id, title, subtitle, image_url, action_type, action_value, sort_order,
                       is_active, starts_at, ends_at
                FROM dashboard_banners
                WHERE is_active = 1
                  AND (starts_at IS NULL OR starts_at <= NOW())
                  AND (ends_at IS NULL OR ends_at >= NOW())
                ORDER BY sort_order ASC, id ASC
                """;
        List<DashboardBanner> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    private static DashboardBanner mapRow(ResultSet rs) throws SQLException {
        DashboardBanner b = new DashboardBanner();
        b.setId(rs.getLong("id"));
        b.setTitle(rs.getString("title"));
        b.setSubtitle(rs.getString("subtitle"));
        b.setImageUrl(rs.getString("image_url"));
        b.setActionType(rs.getString("action_type"));
        b.setActionValue(rs.getString("action_value"));
        b.setSortOrder(rs.getInt("sort_order"));
        b.setActive(rs.getBoolean("is_active"));
        b.setStartsAt(getLocalDateTime(rs, "starts_at"));
        b.setEndsAt(getLocalDateTime(rs, "ends_at"));
        return b;
    }

    private static DashboardBanner mapRowAdmin(ResultSet rs) throws SQLException {
        DashboardBanner b = mapRow(rs);
        b.setCreatedAt(getLocalDateTime(rs, "created_at"));
        b.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        return b;
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static void bindWritable(PreparedStatement ps, DashboardBanner b) throws SQLException {
        ps.setString(1, b.getTitle());
        ps.setString(2, b.getSubtitle());
        ps.setString(3, b.getImageUrl());
        ps.setString(4, b.getActionType());
        ps.setString(5, b.getActionValue());
        ps.setInt(6, b.getSortOrder() != null ? b.getSortOrder() : 0);
        ps.setBoolean(7, Boolean.TRUE.equals(b.getActive()));
        ps.setTimestamp(8, b.getStartsAt() == null ? null : Timestamp.valueOf(b.getStartsAt()));
        ps.setTimestamp(9, b.getEndsAt() == null ? null : Timestamp.valueOf(b.getEndsAt()));
    }
}

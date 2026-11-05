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

import com.narainsons.model.LoanApplyGuideVideo;
import com.narainsons.util.DBConnection;

public class LoanApplyGuideVideoDAO {

    public Optional<LoanApplyGuideVideo> findPrimaryActive() throws SQLException {
        String sql = """
                SELECT * FROM loan_apply_guide_video
                WHERE is_active = 1
                ORDER BY sort_order ASC, id ASC
                LIMIT 1
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public List<LoanApplyGuideVideo> findAllActive() throws SQLException {
        String sql = """
                SELECT * FROM loan_apply_guide_video
                WHERE is_active = 1
                ORDER BY sort_order ASC, id ASC
                """;
        List<LoanApplyGuideVideo> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** id null inserts; otherwise updates that row. @return false if the row to update does not exist */
    public boolean save(Long id, String title, String description, String videoUrl, String thumbnailUrl, String videoType,
            Integer durationSeconds, int sortOrder, boolean active) throws SQLException {
        String sql = id == null
                ? "INSERT INTO loan_apply_guide_video (title, description, video_url, thumbnail_url, video_type, duration_seconds, sort_order, is_active) VALUES (?,?,?,?,?,?,?,?)"
                : "UPDATE loan_apply_guide_video SET title=?, description=?, video_url=?, thumbnail_url=?, video_type=?, duration_seconds=?, sort_order=?, is_active=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, videoUrl);
            ps.setString(4, thumbnailUrl);
            ps.setString(5, videoType);
            ps.setObject(6, durationSeconds);
            ps.setInt(7, sortOrder);
            ps.setBoolean(8, active);
            if (id != null) {
                ps.setLong(9, id);
            }
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM loan_apply_guide_video WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static LoanApplyGuideVideo mapRow(ResultSet rs) throws SQLException {
        LoanApplyGuideVideo v = new LoanApplyGuideVideo();
        v.setId(rs.getLong("id"));
        v.setTitle(rs.getString("title"));
        v.setDescription(rs.getString("description"));
        v.setVideoUrl(rs.getString("video_url"));
        v.setThumbnailUrl(rs.getString("thumbnail_url"));
        v.setVideoType(rs.getString("video_type"));
        int dur = rs.getInt("duration_seconds");
        v.setDurationSeconds(rs.wasNull() ? null : dur);
        v.setLocale(rs.getString("locale"));
        v.setSortOrder(rs.getInt("sort_order"));
        v.setActive(rs.getBoolean("is_active"));
        v.setCreatedAt(getLocalDateTime(rs, "created_at"));
        v.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        return v;
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }
}

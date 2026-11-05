package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import com.narainsons.util.DBConnection;

public class RefreshTokenDAO {

    public record Row(long id, long userId, String familyId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    }

    public void insert(long userId, String tokenHash, String familyId, LocalDateTime expiresAt) throws SQLException {
        String sql = "INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at) VALUES (?,?,?,?)";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, tokenHash);
            ps.setString(3, familyId);
            ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();
        }
    }

    public Optional<Row> findByHash(String tokenHash) throws SQLException {
        String sql = "SELECT * FROM refresh_tokens WHERE token_hash=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp revoked = rs.getTimestamp("revoked_at");
                return Optional.of(new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getString("family_id"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        revoked == null ? null : revoked.toLocalDateTime()));
            }
        }
    }

    /** @return false if it was already revoked, i.e. another request got there first */
    public boolean revoke(long id) throws SQLException {
        return update("UPDATE refresh_tokens SET revoked_at=NOW() WHERE id=? AND revoked_at IS NULL", id) > 0;
    }

    public void revokeFamily(String familyId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE refresh_tokens SET revoked_at=NOW() WHERE family_id=? AND revoked_at IS NULL")) {
            ps.setString(1, familyId);
            ps.executeUpdate();
        }
    }

    public void revokeAllForUser(long userId) throws SQLException {
        update("UPDATE refresh_tokens SET revoked_at=NOW() WHERE user_id=? AND revoked_at IS NULL", userId);
    }

    private static int update(String sql, long id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }
}

package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.narainsons.util.DBConnection;
import com.narainsons.util.Hashing;

public class DeviceDAO {

    /** A token belongs to one phone, so it moves to whoever signed in last on that phone. */
    public void upsert(long userId, String fcmToken, String platform, String deviceId, String appVersion)
            throws SQLException {
        String sql = """
                INSERT INTO user_devices (user_id, fcm_token, fcm_token_hash, platform, device_id, app_version)
                VALUES (?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), platform=VALUES(platform),
                    device_id=VALUES(device_id), app_version=VALUES(app_version), updated_at=NOW()
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, fcmToken);
            ps.setString(3, Hashing.sha256(fcmToken));
            ps.setString(4, platform);
            ps.setString(5, deviceId);
            ps.setString(6, appVersion);
            ps.executeUpdate();
        }
    }

    public List<String> tokensForUser(long userId) throws SQLException {
        List<String> tokens = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT fcm_token FROM user_devices WHERE user_id=?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tokens.add(rs.getString(1));
                }
            }
        }
        return tokens;
    }

    public void removeToken(String fcmToken) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM user_devices WHERE fcm_token_hash=?")) {
            ps.setString(1, Hashing.sha256(fcmToken));
            ps.executeUpdate();
        }
    }

    public void removeTokenForUser(long userId, String fcmToken) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM user_devices WHERE fcm_token_hash=? AND user_id=?")) {
            ps.setString(1, Hashing.sha256(fcmToken));
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void removeAllForUser(long userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM user_devices WHERE user_id=?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }
}

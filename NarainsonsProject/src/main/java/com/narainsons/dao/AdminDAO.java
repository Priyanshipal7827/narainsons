package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.narainsons.model.Admin;
import com.narainsons.util.DBConnection;

public class AdminDAO {

    public long create(Admin admin) throws SQLException {
        String sql = """
                INSERT INTO admins (email, password_hash, full_name, mobile_number, role, account_status)
                VALUES (?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, admin.getEmail());
            ps.setString(2, admin.getPasswordHash());
            ps.setString(3, admin.getFullName());
            ps.setString(4, admin.getMobileNumber());
            ps.setString(5, admin.getRole() == null ? "admin" : admin.getRole());
            ps.setString(6, admin.getAccountStatus() == null ? "active" : admin.getAccountStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Admin insert failed");
    }

    public Optional<Admin> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM admins WHERE LOWER(email)=LOWER(?) AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Admin> findById(long id) throws SQLException {
        String sql = "SELECT * FROM admins WHERE id=? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void markLogin(long adminId) throws SQLException {
        String sql = "UPDATE admins SET last_login=NOW() WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.executeUpdate();
        }
    }

    public void saveOtp(long adminId, String otpHash, LocalDateTime expires) throws SQLException {
        String sql = "UPDATE admins SET otp_hash=?, otp_expires_at=?, otp_attempts=0 WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, otpHash);
            ps.setTimestamp(2, Timestamp.valueOf(expires));
            ps.setLong(3, adminId);
            ps.executeUpdate();
        }
    }

    public void updatePasswordHash(long adminId, String hash) throws SQLException {
        String sql = """
                UPDATE admins SET password_hash=?, otp_hash=NULL, otp_expires_at=NULL, otp_attempts=0,
                    token_version=token_version+1, login_attempts=0, locked_until=NULL
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setLong(2, adminId);
            ps.executeUpdate();
        }
    }

    public void updateProfile(long adminId, String fullName, String mobile) throws SQLException {
        String sql = "UPDATE admins SET full_name=?, mobile_number=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, mobile);
            ps.setLong(3, adminId);
            ps.executeUpdate();
        }
    }

    public void updateProfileImagePath(long adminId, String path) throws SQLException {
        String sql = "UPDATE admins SET profile_image_path=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setLong(2, adminId);
            ps.executeUpdate();
        }
    }

    public void clearProfileImagePath(long adminId) throws SQLException {
        updateProfileImagePath(adminId, null);
    }

    /** Counts a wrong OTP; the fifth one drops the OTP so a new one has to be requested. */
    public void recordOtpFailure(long adminId) throws SQLException {
        String sql = """
                UPDATE admins SET otp_attempts = otp_attempts + 1,
                    otp_hash = IF(otp_attempts >= 5, NULL, otp_hash),
                    otp_expires_at = IF(otp_attempts >= 5, NULL, otp_expires_at)
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.executeUpdate();
        }
    }

    public void recordLoginFailure(long adminId, int maxFailures, LocalDateTime lockUntil) throws SQLException {
        String sql = """
                UPDATE admins SET login_attempts = login_attempts + 1,
                    locked_until = IF(login_attempts >= ?, ?, locked_until),
                    login_attempts = IF(login_attempts >= ?, 0, login_attempts)
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxFailures);
            ps.setTimestamp(2, Timestamp.valueOf(lockUntil));
            ps.setInt(3, maxFailures);
            ps.setLong(4, adminId);
            ps.executeUpdate();
        }
    }

    public void clearLoginFailures(long adminId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE admins SET login_attempts=0, locked_until=NULL WHERE id=?")) {
            ps.setLong(1, adminId);
            ps.executeUpdate();
        }
    }

    /** All admins that are not deleted, inactive ones included. */
    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM admins WHERE deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public boolean updateStatus(long adminId, String status) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE admins SET account_status=? WHERE id=? AND deleted_at IS NULL")) {
            ps.setString(1, status);
            ps.setLong(2, adminId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM admins WHERE deleted_at IS NULL AND account_status='active'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public List<Admin> listAll(int limit) throws SQLException {
        String sql = """
                SELECT * FROM admins WHERE deleted_at IS NULL ORDER BY id ASC LIMIT ?
                """;
        List<Admin> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private static Admin map(ResultSet rs) throws SQLException {
        Admin a = new Admin();
        a.setId(rs.getLong("id"));
        a.setEmail(rs.getString("email"));
        a.setPasswordHash(rs.getString("password_hash"));
        a.setFullName(rs.getString("full_name"));
        a.setMobileNumber(rs.getString("mobile_number"));
        try {
            a.setProfileImagePath(rs.getString("profile_image_path"));
        } catch (SQLException ignored) {
            a.setProfileImagePath(null);
        }
        a.setRole(rs.getString("role"));
        a.setAccountStatus(rs.getString("account_status"));
        a.setTokenVersion(rs.getInt("token_version"));
        a.setOtpHash(rs.getString("otp_hash"));
        Timestamp otpExp = rs.getTimestamp("otp_expires_at");
        a.setOtpExpiresAt(otpExp == null ? null : otpExp.toLocalDateTime());
        Timestamp locked = rs.getTimestamp("locked_until");
        a.setLockedUntil(locked == null ? null : locked.toLocalDateTime());
        Timestamp last = rs.getTimestamp("last_login");
        a.setLastLogin(last == null ? null : last.toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        a.setCreatedAt(created == null ? null : created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        a.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return a;
    }
}

package com.narainsons.dao;

import java.math.BigDecimal;
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

import com.narainsons.util.DBConnection;

public class EmiPaymentDAO {

    public record PaymentRow(
            long id, long emiScheduleId, long userId, long loanApplicationId,
            BigDecimal amountClaimed, String screenshotUrl, String utrReference,
            String status, String adminNote, LocalDateTime submittedAt, LocalDateTime reviewedAt) {
    }

    public long insert(PaymentRow row) throws SQLException {
        String sql = """
                INSERT INTO emi_payment_submissions (
                    emi_schedule_id, user_id, loan_application_id, amount_claimed,
                    screenshot_url, utr_reference, status
                ) VALUES (?,?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, row.emiScheduleId());
            ps.setLong(2, row.userId());
            ps.setLong(3, row.loanApplicationId());
            ps.setBigDecimal(4, row.amountClaimed());
            ps.setString(5, row.screenshotUrl());
            ps.setString(6, row.utrReference());
            ps.setString(7, "pending");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Payment insert failed");
    }

    public Optional<PaymentRow> findById(long id) throws SQLException {
        String sql = "SELECT * FROM emi_payment_submissions WHERE id=?";
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

    public boolean hasPendingForEmi(long emiScheduleId) throws SQLException {
        String sql = "SELECT 1 FROM emi_payment_submissions WHERE emi_schedule_id=? AND status='pending' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, emiScheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<PaymentRow> listByUser(long userId, int limit, int offset) throws SQLException {
        String sql = """
                SELECT * FROM emi_payment_submissions WHERE user_id=?
                ORDER BY submitted_at DESC, id DESC LIMIT ? OFFSET ?
                """;
        List<PaymentRow> list = new ArrayList<>();
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

    public List<PaymentRow> listPendingForAdmin(int limit) throws SQLException {
        String sql = """
                SELECT * FROM emi_payment_submissions WHERE status='pending'
                ORDER BY submitted_at ASC LIMIT ?
                """;
        List<PaymentRow> list = new ArrayList<>();
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

    /** Decides the payment only if it is still pending, so it cannot be decided twice. */
    public boolean reviewPending(long id, String status, String adminNote) throws SQLException {
        String sql = """
                UPDATE emi_payment_submissions SET status=?, admin_note=?, reviewed_at=NOW()
                WHERE id=? AND status='pending'
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, adminNote);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static PaymentRow map(ResultSet rs) throws SQLException {
        Timestamp sub = rs.getTimestamp("submitted_at");
        Timestamp rev = rs.getTimestamp("reviewed_at");
        return new PaymentRow(
                rs.getLong("id"),
                rs.getLong("emi_schedule_id"),
                rs.getLong("user_id"),
                rs.getLong("loan_application_id"),
                rs.getBigDecimal("amount_claimed"),
                rs.getString("screenshot_url"),
                rs.getString("utr_reference"),
                rs.getString("status"),
                rs.getString("admin_note"),
                sub == null ? null : sub.toLocalDateTime(),
                rev == null ? null : rev.toLocalDateTime());
    }
}

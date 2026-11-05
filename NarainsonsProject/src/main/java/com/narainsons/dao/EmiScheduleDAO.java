package com.narainsons.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.narainsons.util.DBConnection;

public class EmiScheduleDAO {

    public record EmiRow(
            long id, long loanApplicationId, long userId, int installmentNumber,
            LocalDate dueDate, BigDecimal amountDue, BigDecimal amountPaid, String status,
            LocalDateTime lastReminderAt, LocalDateTime paidAt) {
    }

    public void insertBatch(List<EmiRow> rows) throws SQLException {
        String sql = """
                INSERT IGNORE INTO loan_emi_schedules (
                    loan_application_id, user_id, installment_number, due_date, amount_due, status
                ) VALUES (?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EmiRow r : rows) {
                ps.setLong(1, r.loanApplicationId());
                ps.setLong(2, r.userId());
                ps.setInt(3, r.installmentNumber());
                ps.setDate(4, Date.valueOf(r.dueDate()));
                ps.setBigDecimal(5, r.amountDue());
                ps.setString(6, "pending");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public boolean existsForLoan(long loanApplicationId) throws SQLException {
        String sql = "SELECT 1 FROM loan_emi_schedules WHERE loan_application_id=? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, loanApplicationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<EmiRow> listByUser(long userId, long loanApplicationId) throws SQLException {
        String sql = """
                SELECT * FROM loan_emi_schedules
                WHERE user_id=? AND loan_application_id=?
                ORDER BY installment_number ASC
                """;
        return queryList(sql, ps -> {
            ps.setLong(1, userId);
            ps.setLong(2, loanApplicationId);
        });
    }

    public Optional<EmiRow> findByIdForUser(long id, long userId) throws SQLException {
        String sql = "SELECT * FROM loan_emi_schedules WHERE id=? AND user_id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<EmiRow> findById(long id) throws SQLException {
        String sql = "SELECT * FROM loan_emi_schedules WHERE id=?";
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

    /** EMIs due by toDate, overdue ones included, on live loans of existing users. */
    public List<EmiRow> findDueForReminders(LocalDate toDate) throws SQLException {
        String sql = """
                SELECT e.* FROM loan_emi_schedules e
                JOIN loan_applications la ON la.id = e.loan_application_id
                JOIN users u ON u.id = e.user_id
                WHERE e.status IN ('pending','overdue') AND e.due_date <= ?
                  AND la.status='disbursed' AND u.deleted_at IS NULL
                """;
        return queryList(sql, ps -> ps.setDate(1, Date.valueOf(toDate)));
    }

    public int markOverdue(LocalDate today) throws SQLException {
        String sql = """
                UPDATE loan_emi_schedules e JOIN loan_applications la ON la.id = e.loan_application_id
                SET e.status='overdue', e.updated_at=NOW()
                WHERE e.status='pending' AND e.due_date < ? AND la.status='disbursed'
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today));
            return ps.executeUpdate();
        }
    }

    /** Call inside DBConnection.inTransaction; holds the row until commit. */
    public Optional<EmiRow> findByIdForUserLocked(long id, long userId) throws SQLException {
        String sql = "SELECT * FROM loan_emi_schedules WHERE id=? AND user_id=? FOR UPDATE";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void markAwaitingProof(long id) throws SQLException {
        updateStatus(id, "awaiting_proof");
    }

    public void markPaid(long id, BigDecimal amount) throws SQLException {
        String sql = """
                UPDATE loan_emi_schedules SET status='paid', amount_paid=?, paid_at=NOW(), updated_at=NOW()
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** EMIs run from the day money was sent: first due one month after disbursal. */
    public void setDueDatesFrom(long loanApplicationId, LocalDate disbursedOn) throws SQLException {
        String sql = "UPDATE loan_emi_schedules SET due_date=DATE_ADD(?, INTERVAL installment_number MONTH) WHERE loan_application_id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(disbursedOn));
            ps.setLong(2, loanApplicationId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(long id, String status) throws SQLException {
        String sql = "UPDATE loan_emi_schedules SET status=?, updated_at=NOW() WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void touchReminder(long id) throws SQLException {
        String sql = "UPDATE loan_emi_schedules SET last_reminder_at=NOW() WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public int countPending(long userId, long loanId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM loan_emi_schedules
                WHERE user_id=? AND loan_application_id=? AND status IN ('pending','overdue','awaiting_proof')
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @FunctionalInterface
    private interface PsBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<EmiRow> queryList(String sql, PsBinder binder) throws SQLException {
        List<EmiRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private static EmiRow map(ResultSet rs) throws SQLException {
        Date due = rs.getDate("due_date");
        Timestamp lastR = rs.getTimestamp("last_reminder_at");
        Timestamp paid = rs.getTimestamp("paid_at");
        return new EmiRow(
                rs.getLong("id"),
                rs.getLong("loan_application_id"),
                rs.getLong("user_id"),
                rs.getInt("installment_number"),
                due.toLocalDate(),
                rs.getBigDecimal("amount_due"),
                rs.getBigDecimal("amount_paid"),
                rs.getString("status"),
                lastR == null ? null : lastR.toLocalDateTime(),
                paid == null ? null : paid.toLocalDateTime());
    }
}

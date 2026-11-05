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

import com.narainsons.model.LoanApplication;
import com.narainsons.util.DBConnection;

public class LoanApplicationDAO {

    public long create(LoanApplication app) throws SQLException {
        String sql = """
                INSERT INTO loan_applications (
                    user_id, application_code, requested_amount, tenure_months, purpose, purpose_detail,
                    monthly_income, employment_type, company_name, bank_name, ifsc_code, bank_account_last4,
                    status, status_message, interest_rate_annual, processing_fee, total_payable, emi_amount
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setLong(i++, app.getUserId());
            ps.setString(i++, app.getApplicationCode());
            ps.setBigDecimal(i++, app.getRequestedAmount());
            ps.setInt(i++, app.getTenureMonths());
            ps.setString(i++, app.getPurpose());
            ps.setString(i++, app.getPurposeDetail());
            ps.setBigDecimal(i++, app.getMonthlyIncome());
            ps.setString(i++, app.getEmploymentType());
            ps.setString(i++, app.getCompanyName());
            ps.setString(i++, app.getBankName());
            ps.setString(i++, app.getIfscCode());
            ps.setString(i++, app.getBankAccountLast4());
            ps.setString(i++, app.getStatus());
            ps.setString(i++, app.getStatusMessage());
            ps.setBigDecimal(i++, app.getInterestRateAnnual());
            ps.setBigDecimal(i++, app.getProcessingFee());
            ps.setBigDecimal(i++, app.getTotalPayable());
            ps.setBigDecimal(i++, app.getEmiAmount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Loan application insert failed");
    }

    public Optional<LoanApplication> findByIdAndUser(long id, long userId) throws SQLException {
        String sql = "SELECT * FROM loan_applications WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<LoanApplication> findLatestByUser(long userId) throws SQLException {
        String sql = """
                SELECT * FROM loan_applications WHERE user_id = ?
                ORDER BY created_at DESC LIMIT 1
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** True while a loan is pending, or approved with EMIs still unpaid. */
    public boolean hasOpenLoanForUser(long userId) throws SQLException {
        String sql = """
                SELECT 1 FROM loan_applications la
                WHERE la.user_id=? AND (la.status='pending' OR (la.status IN ('approved','disbursed') AND (
                    NOT EXISTS (SELECT 1 FROM loan_emi_schedules e WHERE e.loan_application_id=la.id)
                    OR EXISTS (SELECT 1 FROM loan_emi_schedules e WHERE e.loan_application_id=la.id AND e.status<>'paid'))))
                LIMIT 1
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Moves the loan only if it is still pending, so two admins cannot both decide it. */
    public boolean decidePending(long id, String status, String statusMessage, String adminNote) throws SQLException {
        String sql = """
                UPDATE loan_applications SET status=?, status_message=?, admin_note=?, reviewed_at=NOW()
                WHERE id=? AND status='pending'
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, statusMessage);
            ps.setString(3, adminNote);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean cancelPending(long id, long userId, String statusMessage) throws SQLException {
        String sql = "UPDATE loan_applications SET status='cancelled', status_message=? WHERE id=? AND user_id=? AND status='pending'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statusMessage);
            ps.setLong(2, id);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markDisbursed(long id, String reference, String statusMessage, LocalDateTime when) throws SQLException {
        String sql = """
                UPDATE loan_applications SET status='disbursed', disbursed_at=?, disbursement_reference=?, status_message=?
                WHERE id=? AND status='approved'
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(when));
            ps.setString(2, reference);
            ps.setString(3, statusMessage);
            ps.setLong(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markClosed(long id, String statusMessage) throws SQLException {
        String sql = "UPDATE loan_applications SET status='closed', closed_at=NOW(), status_message=? WHERE id=? AND status='disbursed'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, statusMessage);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean hasPendingForUser(long userId) throws SQLException {
        String sql = "SELECT 1 FROM loan_applications WHERE user_id = ? AND status = 'pending' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<LoanApplication> findById(long id) throws SQLException {
        String sql = "SELECT * FROM loan_applications WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<LoanApplication> findLatestApprovedByUser(long userId) throws SQLException {
        String sql = """
                SELECT la.* FROM loan_applications la
                WHERE la.user_id = ? AND la.status IN ('approved','disbursed')
                ORDER BY (NOT EXISTS (SELECT 1 FROM loan_emi_schedules e WHERE e.loan_application_id=la.id)
                       OR EXISTS (SELECT 1 FROM loan_emi_schedules e WHERE e.loan_application_id=la.id AND e.status<>'paid')) DESC,
                       la.id ASC
                LIMIT 1
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void updateStatus(long id, String status, String statusMessage, String adminNote) throws SQLException {
        String sql = """
                UPDATE loan_applications SET status=?, status_message=?, admin_note=?,
                reviewed_at=NOW(), updated_at=NOW() WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, statusMessage);
            ps.setString(3, adminNote);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public int countByStatus(String status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loan_applications WHERE status=?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM loan_applications";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public List<LoanApplication> listByStatus(String status, int limit) throws SQLException {
        String sql = """
                SELECT * FROM loan_applications WHERE status=?
                ORDER BY created_at ASC LIMIT ?
                """;
        List<LoanApplication> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int countForAdmin(String status, String search) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM loan_applications la
                LEFT JOIN users u ON u.id = la.user_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        appendAdminFilters(sql, params, status, search);
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<AdminLoanRow> listForAdmin(String status, String search, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT la.*, u.name AS user_name, u.email AS user_email, u.mobile_number AS user_mobile
                FROM loan_applications la
                LEFT JOIN users u ON u.id = la.user_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        appendAdminFilters(sql, params, status, search);
        sql.append(" ORDER BY la.created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        List<AdminLoanRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminLoanRow row = new AdminLoanRow();
                    row.application = mapRow(rs);
                    row.userName = rs.getString("user_name");
                    row.userEmail = rs.getString("user_email");
                    row.userMobile = rs.getString("user_mobile");
                    list.add(row);
                }
            }
        }
        return list;
    }

    private static void appendAdminFilters(StringBuilder sql, List<Object> params, String status, String search) {
        if (status != null && !status.isBlank()) {
            sql.append(" AND la.status = ?");
            params.add(status.trim().toLowerCase());
        }
        if (search != null && !search.isBlank()) {
            String q = "%" + search.trim() + "%";
            sql.append("""
                     AND (la.application_code LIKE ? OR la.purpose LIKE ? OR la.purpose_detail LIKE ?
                          OR CAST(la.id AS CHAR) LIKE ? OR CAST(la.user_id AS CHAR) LIKE ?
                          OR u.name LIKE ? OR u.email LIKE ? OR u.mobile_number LIKE ?)
                    """);
            for (int i = 0; i < 8; i++) {
                params.add(q);
            }
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer n) {
                ps.setInt(i + 1, n);
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    public static final class AdminLoanRow {
        public LoanApplication application;
        public String userName;
        public String userEmail;
        public String userMobile;
    }

    public List<LoanApplication> listByUser(long userId, int limit) throws SQLException {
        return listByUser(userId, limit, 0);
    }

    public List<LoanApplication> listByUser(long userId, int limit, int offset) throws SQLException {
        String sql = """
                SELECT * FROM loan_applications WHERE user_id = ?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """;
        List<LoanApplication> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private static LoanApplication mapRow(ResultSet rs) throws SQLException {
        LoanApplication a = new LoanApplication();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        a.setApplicationCode(rs.getString("application_code"));
        a.setRequestedAmount(rs.getBigDecimal("requested_amount"));
        a.setTenureMonths(rs.getInt("tenure_months"));
        a.setPurpose(rs.getString("purpose"));
        a.setPurposeDetail(rs.getString("purpose_detail"));
        a.setMonthlyIncome(getBigDecimal(rs, "monthly_income"));
        a.setEmploymentType(rs.getString("employment_type"));
        a.setCompanyName(rs.getString("company_name"));
        a.setBankName(rs.getString("bank_name"));
        a.setIfscCode(rs.getString("ifsc_code"));
        a.setBankAccountLast4(rs.getString("bank_account_last4"));
        a.setStatus(rs.getString("status"));
        a.setStatusMessage(rs.getString("status_message"));
        a.setAdminNote(rs.getString("admin_note"));
        a.setReviewedAt(getLocalDateTime(rs, "reviewed_at"));
        a.setCreatedAt(getLocalDateTime(rs, "created_at"));
        a.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        a.setInterestRateAnnual(rs.getBigDecimal("interest_rate_annual"));
        a.setProcessingFee(rs.getBigDecimal("processing_fee"));
        a.setTotalPayable(rs.getBigDecimal("total_payable"));
        a.setEmiAmount(rs.getBigDecimal("emi_amount"));
        a.setDisbursedAt(getLocalDateTime(rs, "disbursed_at"));
        a.setDisbursementReference(rs.getString("disbursement_reference"));
        a.setClosedAt(getLocalDateTime(rs, "closed_at"));
        return a;
    }

    private static BigDecimal getBigDecimal(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return rs.wasNull() ? null : v;
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }
}

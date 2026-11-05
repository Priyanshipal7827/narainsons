package com.narainsons.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.dao.LoanGuarantorDAO;
import com.narainsons.model.LoanApplication;
import com.narainsons.model.LoanGuarantor;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;
import com.narainsons.util.ServletUtil;

/** Dashboard figures and the full view of one loan. */
public class AdminOverviewService {

    private static final int MAX_DAYS = 366;

    public Map<String, Object> dashboard(String fromRaw, String toRaw) throws SQLException {
        LocalDate to = parse(toRaw, LocalDate.now());
        LocalDate from = parse(fromRaw, to.minusDays(29));
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("date_from must be before date_to");
        }
        if (from.isBefore(to.minusDays(MAX_DAYS))) {
            from = to.minusDays(MAX_DAYS);
        }
        java.sql.Date a = java.sql.Date.valueOf(from);
        java.sql.Date b = java.sql.Date.valueOf(to.plusDays(1));
        LocalDate today = LocalDate.now();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date_from", from.toString());
        m.put("date_to", to.toString());

        // Work waiting for staff: these ignore the date range, because they need doing either way.
        m.put("pending_loans", num("SELECT COUNT(*) FROM loan_applications WHERE status='pending'"));
        m.put("approved_to_disburse", num("SELECT COUNT(*) FROM loan_applications WHERE status='approved'"));
        m.put("kyc_to_review", num("SELECT COUNT(*) FROM users WHERE kyc_status='submitted' AND deleted_at IS NULL"));
        m.put("emi_proofs_pending", num("SELECT COUNT(*) FROM emi_payment_submissions WHERE status='pending'"));

        // Current position of the book.
        m.put("total_users", num("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL"));
        m.put("active_loans", num("SELECT COUNT(*) FROM loan_applications WHERE status='disbursed'"));
        m.put("closed_loans", num("SELECT COUNT(*) FROM loan_applications WHERE status='closed'"));
        m.put("rejected_loans", num("SELECT COUNT(*) FROM loan_applications WHERE status='rejected'"));
        m.put("outstanding_amount", num("""
                SELECT COALESCE(SUM(e.amount_due),0) FROM loan_emi_schedules e
                JOIN loan_applications la ON la.id=e.loan_application_id
                WHERE la.status='disbursed' AND e.status<>'paid'"""));
        m.put("overdue_emis", num("""
                SELECT COUNT(*) FROM loan_emi_schedules e JOIN loan_applications la ON la.id=e.loan_application_id
                WHERE la.status='disbursed' AND e.status='overdue'"""));
        m.put("overdue_amount", num("""
                SELECT COALESCE(SUM(e.amount_due),0) FROM loan_emi_schedules e JOIN loan_applications la ON la.id=e.loan_application_id
                WHERE la.status='disbursed' AND e.status='overdue'"""));
        m.put("due_next_7_days", num("SELECT COUNT(*) FROM loan_emi_schedules e JOIN loan_applications la ON la.id=e.loan_application_id"
                + " WHERE la.status='disbursed' AND e.status='pending' AND e.due_date >= ? AND e.due_date <= ?",
                java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.plusDays(7))));

        // Activity inside the chosen dates.
        m.put("new_users", num("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND created_at >= ? AND created_at < ?", a, b));
        m.put("applications", num("SELECT COUNT(*) FROM loan_applications WHERE created_at >= ? AND created_at < ?", a, b));
        m.put("amount_requested", num("SELECT COALESCE(SUM(requested_amount),0) FROM loan_applications WHERE created_at >= ? AND created_at < ?", a, b));
        m.put("amount_disbursed", num("SELECT COALESCE(SUM(requested_amount),0) FROM loan_applications WHERE disbursed_at >= ? AND disbursed_at < ?", a, b));
        m.put("amount_collected", num("SELECT COALESCE(SUM(amount_paid),0) FROM loan_emi_schedules WHERE status='paid' AND paid_at >= ? AND paid_at < ?", a, b));

        m.put("series_applications", series("loan_applications", "created_at", "", from, to));
        m.put("series_users", series("users", "created_at", " AND deleted_at IS NULL", from, to));
        return m;
    }

    public Map<String, Object> loanDetail(long loanId) throws SQLException {
        LoanApplication loan = new LoanApplicationDAO().findById(loanId)
                .orElseThrow(() -> ApiException.notFound("Loan application not found"));
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> app = new LinkedHashMap<>(DashboardService.loanPayload(loan));
        app.put("user_id", loan.getUserId());
        app.put("monthly_income", loan.getMonthlyIncome());
        app.put("employment_type", loan.getEmploymentType());
        app.put("company_name", loan.getCompanyName());
        app.put("bank_name", loan.getBankName());
        app.put("ifsc_code", loan.getIfscCode());
        app.put("bank_account_last4", loan.getBankAccountLast4());
        app.put("admin_note", loan.getAdminNote());
        List<Map<String, Object>> guarantors = new ArrayList<>();
        for (LoanGuarantor g : new LoanGuarantorDAO().findByLoanApplicationId(loanId)) {
            guarantors.add(DashboardService.guarantorPayload(g));
        }
        app.put("guarantors", guarantors);
        data.put("application", app);
        return data;
    }

    private static LocalDate parse(String raw, LocalDate fallback) {
        if (ServletUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must look like 2026-11-05");
        }
    }

    private static BigDecimal num(String sql, Object... params) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    /** One entry per day, zero included, so the chart's axis stays even. */
    private static List<Map<String, Object>> series(String table, String column, String extra, LocalDate from, LocalDate to)
            throws SQLException {
        String sql = "SELECT DATE(" + column + "), COUNT(*) FROM " + table + " WHERE " + column + " >= ? AND " + column + " < ?"
                + extra + " GROUP BY DATE(" + column + ")";
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to.plusDays(1)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.toString());
            point.put("count", counts.getOrDefault(d.toString(), 0L));
            out.add(point);
        }
        return out;
    }
}

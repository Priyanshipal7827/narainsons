package com.narainsons.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.narainsons.model.Admin;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;
import com.narainsons.util.ServletUtil;
import com.narainsons.util.TableExport;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One way to list, search, filter by date, sort, page and export every admin table.
 * All SQL pieces come from the definitions below; request values only ever reach SQL as bound
 * parameters or after being matched against these lists. Columns not listed here (password hashes,
 * tokens, contacts) cannot be returned or exported.
 */
public class AdminListService {

    public static final Set<Integer> PAGE_SIZES = Set.of(25, 50, 100, 200, 500);
    private static final int EXPORT_MAX_ROWS = 100_000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private record Filter(String column, String op) {
    }

    private static final class Dataset {
        final String from;
        final String where;
        final Map<String, String> columns = new LinkedHashMap<>();
        final List<String> search = new ArrayList<>();
        final Map<String, Filter> filters = new LinkedHashMap<>();
        String dateColumn;
        String defaultSort;
        String statusFilter;
        boolean superOnly;

        Dataset(String from, String where) {
            this.from = from;
            this.where = where;
        }

        Dataset cols(String... aliasThenExpr) {
            for (int i = 0; i < aliasThenExpr.length; i += 2) {
                columns.put(aliasThenExpr[i], aliasThenExpr[i + 1]);
            }
            return this;
        }

        Dataset search(String... exprs) {
            search.addAll(List.of(exprs));
            return this;
        }

        Dataset eq(String param, String column) {
            filters.put(param, new Filter(column, "="));
            return this;
        }

        Dataset range(String param, String column) {
            filters.put(param + "_min", new Filter(column, ">="));
            filters.put(param + "_max", new Filter(column, "<="));
            return this;
        }

        Dataset date(String column) {
            dateColumn = column;
            return this;
        }

        Dataset sort(String orderBy) {
            defaultSort = orderBy;
            return this;
        }

        /** Param whose per-value counts drive the clickable cards above a table. */
        Dataset status(String param) {
            statusFilter = param;
            return this;
        }

        Dataset superOnly() {
            superOnly = true;
            return this;
        }
    }

    private static final Map<String, Dataset> DATASETS = new LinkedHashMap<>();

    static {
        DATASETS.put("users", new Dataset("users u", "u.deleted_at IS NULL")
                .cols("id", "u.id", "customer_code", "u.customer_code", "name", "u.name", "mobile_number", "u.mobile_number",
                        "email", "u.email", "gender", "u.gender", "date_of_birth", "u.date_of_birth",
                        "current_city", "u.current_city", "current_state", "u.current_state",
                        "employment_type", "u.employment_type", "company_name", "u.company_name", "monthly_income", "u.monthly_income",
                        "bank_name", "u.bank_name", "bank_account_last4", "u.bank_account_last4", "ifsc_code", "u.ifsc_code",
                        "bank_verification_status", "u.bank_verification_status", "kyc_status", "u.kyc_status",
                        "account_status", "u.account_status", "onboarding_step", "u.onboarding_step",
                        "last_login", "u.last_login", "created_at", "u.created_at")
                .search("CAST(u.id AS CHAR)", "u.customer_code", "u.name", "u.mobile_number", "u.email", "u.current_city")
                .eq("kyc_status", "u.kyc_status").eq("account_status", "u.account_status").eq("onboarding_step", "u.onboarding_step")
                .eq("employment_type", "u.employment_type").eq("gender", "u.gender").range("income", "u.monthly_income")
                .date("u.created_at").sort("u.id DESC").status("kyc_status"));

        DATASETS.put("loans", new Dataset("loan_applications la LEFT JOIN users u ON u.id = la.user_id", "1=1")
                .cols("id", "la.id", "application_code", "la.application_code", "user_id", "la.user_id", "customer", "u.name",
                        "mobile_number", "u.mobile_number", "email", "u.email", "kyc_status", "u.kyc_status",
                        "requested_amount", "la.requested_amount", "tenure_months", "la.tenure_months",
                        "interest_rate_annual", "la.interest_rate_annual", "processing_fee", "la.processing_fee",
                        "emi_amount", "la.emi_amount", "total_payable", "la.total_payable", "purpose", "la.purpose",
                        "purpose_detail", "la.purpose_detail", "monthly_income", "la.monthly_income",
                        "employment_type", "la.employment_type", "company_name", "la.company_name", "bank_name", "la.bank_name",
                        "ifsc_code", "la.ifsc_code", "bank_account_last4", "la.bank_account_last4", "status", "la.status",
                        "admin_note", "la.admin_note", "disbursement_reference", "la.disbursement_reference",
                        "reviewed_at", "la.reviewed_at", "disbursed_at", "la.disbursed_at", "closed_at", "la.closed_at", "created_at", "la.created_at")
                .search("CAST(la.id AS CHAR)", "la.application_code", "la.purpose", "la.disbursement_reference", "u.name", "u.mobile_number", "u.email")
                .eq("status", "la.status").eq("purpose", "la.purpose").eq("kyc_status", "u.kyc_status").eq("user_id", "la.user_id")
                .range("amount", "la.requested_amount").range("tenure", "la.tenure_months")
                .date("la.created_at").sort("la.id DESC").status("status"));

        DATASETS.put("emi_payments", new Dataset("""
                emi_payment_submissions p
                LEFT JOIN users u ON u.id = p.user_id
                LEFT JOIN loan_applications la ON la.id = p.loan_application_id
                LEFT JOIN loan_emi_schedules e ON e.id = p.emi_schedule_id""", "1=1")
                .cols("id", "p.id", "application_code", "la.application_code", "loan_application_id", "p.loan_application_id",
                        "user_id", "p.user_id", "customer", "u.name", "mobile_number", "u.mobile_number",
                        "installment_number", "e.installment_number", "due_date", "e.due_date", "amount_due", "e.amount_due",
                        "amount_claimed", "p.amount_claimed", "utr_reference", "p.utr_reference", "screenshot_url", "p.screenshot_url",
                        "status", "p.status", "admin_note", "p.admin_note", "submitted_at", "p.submitted_at", "reviewed_at", "p.reviewed_at")
                .search("CAST(p.id AS CHAR)", "la.application_code", "p.utr_reference", "u.name", "u.mobile_number")
                .eq("status", "p.status").eq("user_id", "p.user_id").eq("loan_id", "p.loan_application_id").range("amount", "p.amount_claimed")
                .date("p.submitted_at").sort("p.id DESC").status("status"));

        DATASETS.put("emi_schedules", new Dataset("""
                loan_emi_schedules e
                LEFT JOIN users u ON u.id = e.user_id
                LEFT JOIN loan_applications la ON la.id = e.loan_application_id""", "1=1")
                .cols("id", "e.id", "application_code", "la.application_code", "loan_application_id", "e.loan_application_id",
                        "user_id", "e.user_id", "customer", "u.name", "mobile_number", "u.mobile_number",
                        "installment_number", "e.installment_number", "due_date", "e.due_date", "amount_due", "e.amount_due",
                        "amount_paid", "e.amount_paid", "status", "e.status", "loan_status", "la.status",
                        "last_reminder_at", "e.last_reminder_at", "paid_at", "e.paid_at")
                .search("CAST(e.id AS CHAR)", "la.application_code", "u.name", "u.mobile_number")
                .eq("status", "e.status").eq("loan_status", "la.status").eq("loan_id", "e.loan_application_id").eq("user_id", "e.user_id")
                .range("amount", "e.amount_due").date("e.due_date").sort("e.due_date ASC, e.id ASC").status("status"));

        DATASETS.put("guarantors", new Dataset("""
                loan_guarantors g
                LEFT JOIN loan_applications la ON la.id = g.loan_application_id
                LEFT JOIN users u ON u.id = la.user_id""", "1=1")
                .cols("id", "g.id", "loan_application_id", "g.loan_application_id", "application_code", "la.application_code",
                        "customer", "u.name", "guarantor_index", "g.guarantor_index", "full_name", "g.full_name",
                        "mobile_number", "g.mobile_number", "relation", "g.relation", "address", "g.address",
                        "aadhaar_last4", "g.aadhaar_last4", "pan_last4", "g.pan_last4", "created_at", "g.created_at")
                .search("g.full_name", "g.mobile_number", "la.application_code", "u.name")
                .eq("relation", "g.relation").eq("loan_id", "g.loan_application_id").date("g.created_at").sort("g.id DESC"));

        DATASETS.put("user_notifications", new Dataset("user_notifications n LEFT JOIN users u ON u.id = n.user_id", "1=1")
                .cols("id", "n.id", "user_id", "n.user_id", "customer", "u.name", "category", "n.category", "title", "n.title",
                        "body", "n.body", "read_at", "n.read_at", "created_at", "n.created_at")
                .search("n.title", "n.body", "u.name", "u.mobile_number")
                .eq("category", "n.category").eq("user_id", "n.user_id").date("n.created_at").sort("n.id DESC").status("category"));

        DATASETS.put("banners", new Dataset("dashboard_banners b", "1=1")
                .cols("id", "b.id", "title", "b.title", "subtitle", "b.subtitle", "image_url", "b.image_url", "action_type", "b.action_type",
                        "action_value", "b.action_value", "sort_order", "b.sort_order", "is_active", "b.is_active",
                        "starts_at", "b.starts_at", "ends_at", "b.ends_at", "created_at", "b.created_at")
                .search("b.title", "b.subtitle").eq("is_active", "b.is_active").eq("action_type", "b.action_type")
                .date("b.created_at").sort("b.sort_order ASC, b.id ASC"));

        DATASETS.put("videos", new Dataset("loan_apply_guide_video v", "1=1")
                .cols("id", "v.id", "title", "v.title", "description", "v.description", "video_url", "v.video_url",
                        "thumbnail_url", "v.thumbnail_url", "video_type", "v.video_type", "duration_seconds", "v.duration_seconds",
                        "locale", "v.locale", "sort_order", "v.sort_order", "is_active", "v.is_active", "created_at", "v.created_at")
                .search("v.title", "v.description").eq("is_active", "v.is_active").eq("video_type", "v.video_type")
                .date("v.created_at").sort("v.sort_order ASC, v.id ASC"));

        DATASETS.put("emi_settings", new Dataset("emi_collection_settings s", "1=1")
                .cols("id", "s.id", "payee_name", "s.payee_name", "upi_id", "s.upi_id", "qr_image_url", "s.qr_image_url",
                        "bank_name", "s.bank_name", "bank_account_name", "s.bank_account_name", "bank_account_number", "s.bank_account_number",
                        "ifsc_code", "s.ifsc_code", "payment_note", "s.payment_note", "is_active", "s.is_active", "updated_at", "s.updated_at")
                .search("s.payee_name", "s.upi_id", "s.bank_name").eq("is_active", "s.is_active").date("s.updated_at").sort("s.id DESC"));

        DATASETS.put("admin_alerts", new Dataset("admin_notifications a", "1=1")
                .cols("id", "a.id", "type", "a.type", "title", "a.title", "body", "a.body", "link", "a.link", "created_at", "a.created_at")
                .search("a.title", "a.body").eq("type", "a.type").date("a.created_at").sort("a.id DESC").status("type"));

        DATASETS.put("admins", new Dataset("admins a", "a.deleted_at IS NULL")
                .cols("id", "a.id", "full_name", "a.full_name", "email", "a.email", "mobile_number", "a.mobile_number", "role", "a.role",
                        "account_status", "a.account_status", "last_login", "a.last_login", "created_at", "a.created_at")
                .search("a.full_name", "a.email", "a.mobile_number").eq("role", "a.role").eq("account_status", "a.account_status")
                .date("a.created_at").sort("a.id ASC").status("account_status").superOnly());
    }

    /** The WHERE clause and its values for one request. */
    private record Query(String where, List<Object> params) {
    }

    public Map<String, Object> list(String name, Admin admin, HttpServletRequest request) throws SQLException {
        Dataset ds = dataset(name, admin);
        int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 25, 1, 500);
        if (!PAGE_SIZES.contains(limit)) {
            limit = 25;
        }
        int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, Integer.MAX_VALUE);
        Query query = where(ds, request, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", count(ds, query));
        data.put("limit", limit);
        data.put("offset", offset);
        data.put("rows", rows(ds, query, order(ds, request), limit, offset));
        if (ds.statusFilter != null) {
            data.put("counts", statusCounts(ds, request));
        }
        return data;
    }

    /** Checks access and filters. Call before an export starts writing, while an error can still be returned. */
    public void checkExport(String name, Admin admin, HttpServletRequest request) {
        where(dataset(name, admin), request, false);
    }

    public void export(String name, Admin admin, HttpServletRequest request, TableExport writer)
            throws SQLException, IOException {
        Dataset ds = dataset(name, admin);
        Query query = where(ds, request, false);
        writer.begin(new ArrayList<>(ds.columns.keySet()));
        String sql = "SELECT " + selectList(ds) + " FROM " + ds.from + " WHERE " + query.where()
                + " ORDER BY " + order(ds, request) + " LIMIT " + EXPORT_MAX_ROWS;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, query.params());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= ds.columns.size(); i++) {
                        row.add(cell(rs.getObject(i)));
                    }
                    writer.row(row);
                }
            }
        }
        writer.finish();
    }

    private static Dataset dataset(String name, Admin admin) {
        Dataset ds = DATASETS.get(name);
        if (ds == null) {
            throw ApiException.notFound("Unknown list: " + name);
        }
        if (ds.superOnly && !"super_admin".equals(admin.getRole())) {
            throw ApiException.forbidden("Only a super admin can view this list");
        }
        return ds;
    }

    private static String selectList(Dataset ds) {
        List<String> parts = new ArrayList<>();
        ds.columns.forEach((alias, expr) -> parts.add(expr + " AS " + alias));
        return String.join(", ", parts);
    }

    private static Query where(Dataset ds, HttpServletRequest request, boolean skipStatus) {
        StringBuilder sql = new StringBuilder(ds.where);
        List<Object> params = new ArrayList<>();

        String search = request.getParameter("search");
        if (!ServletUtil.isBlank(search) && !ds.search.isEmpty()) {
            List<String> likes = new ArrayList<>();
            for (String expr : ds.search) {
                likes.add(expr + " LIKE ?");
                params.add("%" + search.trim() + "%");
            }
            sql.append(" AND (").append(String.join(" OR ", likes)).append(")");
        }
        ds.filters.forEach((param, filter) -> {
            if (skipStatus && param.equals(ds.statusFilter)) {
                return;
            }
            String value = request.getParameter(param);
            if (!ServletUtil.isBlank(value)) {
                sql.append(" AND ").append(filter.column()).append(' ').append(filter.op()).append(" ?");
                params.add(value.trim());
            }
        });
        if (ds.dateColumn != null) {
            LocalDate from = parseDate(request.getParameter("date_from"), "date_from");
            LocalDate to = parseDate(request.getParameter("date_to"), "date_to");
            if (from != null) {
                sql.append(" AND ").append(ds.dateColumn).append(" >= ?");
                params.add(java.sql.Date.valueOf(from));
            }
            if (to != null) {
                sql.append(" AND ").append(ds.dateColumn).append(" < ?");
                params.add(java.sql.Date.valueOf(to.plusDays(1)));
            }
        }
        return new Query(sql.toString(), params);
    }

    private static LocalDate parseDate(String raw, String name) {
        if (ServletUtil.isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must look like 2026-11-05");
        }
    }

    private static String order(Dataset ds, HttpServletRequest request) {
        String column = ds.columns.get(request.getParameter("sort"));
        if (column == null) {
            return ds.defaultSort;
        }
        return column + ("asc".equalsIgnoreCase(request.getParameter("dir")) ? " ASC" : " DESC");
    }

    private static long count(Dataset ds, Query query) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + ds.from + " WHERE " + query.where())) {
            bind(ps, query.params());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static List<Map<String, Object>> rows(Dataset ds, Query query, String orderBy, int limit, int offset)
            throws SQLException {
        String sql = "SELECT " + selectList(ds) + " FROM " + ds.from + " WHERE " + query.where()
                + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<String> aliases = new ArrayList<>(ds.columns.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, query.params());
            ps.setInt(query.params().size() + 1, limit);
            ps.setInt(query.params().size() + 2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < aliases.size(); i++) {
                        row.put(aliases.get(i), cell(rs.getObject(i + 1)));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Counts per status with every other filter applied, so the cards agree with the table. */
    private static Map<String, Long> statusCounts(Dataset ds, HttpServletRequest request) throws SQLException {
        Query query = where(ds, request, true);
        String column = ds.filters.get(ds.statusFilter).column();
        String sql = "SELECT " + column + ", COUNT(*) FROM " + ds.from + " WHERE " + query.where() + " GROUP BY " + column;
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, query.params());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(String.valueOf(rs.getString(1)), rs.getLong(2));
                }
            }
        }
        return counts;
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static Object cell(Object value) {
        if (value instanceof Timestamp ts) {
            return DATE_TIME.format(ts.toLocalDateTime());
        }
        if (value instanceof LocalDateTime dt) {
            return DATE_TIME.format(dt);
        }
        if (value instanceof java.sql.Date || value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return value;
    }
}

package com.narainsons.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Time;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.narainsons.util.DBConnection;

/**
 * Read-only browse of whitelisted narainson_db tables for admin panel.
 */
public class AdminDatabaseBrowseDAO {

    private static final Set<String> SENSITIVE_COLUMNS = Set.of(
            "password_hash", "otp_hash", "session_token",
            "aadhaar_encrypted", "pan_encrypted", "bank_account_encrypted",
            "fcm_token", "password", "contacts_data", "gallery_data");

    private static final Map<String, String> LIST_SQL = new LinkedHashMap<>();

    static {
        LIST_SQL.put("users", "SELECT * FROM users WHERE deleted_at IS NULL ORDER BY id DESC LIMIT ?");
        LIST_SQL.put("admins", "SELECT * FROM admins WHERE deleted_at IS NULL ORDER BY id DESC LIMIT ?");
        LIST_SQL.put("dashboard_banners", "SELECT * FROM dashboard_banners ORDER BY sort_order ASC, id ASC LIMIT ?");
        LIST_SQL.put("emi_collection_settings", "SELECT * FROM emi_collection_settings ORDER BY id DESC LIMIT ?");
        LIST_SQL.put("emi_payment_submissions", "SELECT * FROM emi_payment_submissions ORDER BY submitted_at DESC LIMIT ?");
        LIST_SQL.put("user_notifications", "SELECT * FROM user_notifications ORDER BY created_at DESC LIMIT ?");
        LIST_SQL.put("loan_emi_schedules", "SELECT * FROM loan_emi_schedules ORDER BY due_date DESC, id DESC LIMIT ?");
        LIST_SQL.put("loan_apply_guide_video", "SELECT * FROM loan_apply_guide_video ORDER BY sort_order ASC, id ASC LIMIT ?");
        LIST_SQL.put("loan_applications", "SELECT * FROM loan_applications ORDER BY created_at DESC LIMIT ?");
        LIST_SQL.put("loan_guarantors", "SELECT * FROM loan_guarantors ORDER BY id DESC LIMIT ?");
    }

    private static final Map<String, String> COUNT_SQL = new LinkedHashMap<>();

    static {
        COUNT_SQL.put("users", "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL");
        COUNT_SQL.put("admins", "SELECT COUNT(*) FROM admins WHERE deleted_at IS NULL");
        COUNT_SQL.put("dashboard_banners", "SELECT COUNT(*) FROM dashboard_banners");
        COUNT_SQL.put("emi_collection_settings", "SELECT COUNT(*) FROM emi_collection_settings");
        COUNT_SQL.put("emi_payment_submissions", "SELECT COUNT(*) FROM emi_payment_submissions");
        COUNT_SQL.put("user_notifications", "SELECT COUNT(*) FROM user_notifications");
        COUNT_SQL.put("loan_emi_schedules", "SELECT COUNT(*) FROM loan_emi_schedules");
        COUNT_SQL.put("loan_apply_guide_video", "SELECT COUNT(*) FROM loan_apply_guide_video");
        COUNT_SQL.put("loan_applications", "SELECT COUNT(*) FROM loan_applications");
        COUNT_SQL.put("loan_guarantors", "SELECT COUNT(*) FROM loan_guarantors");
    }

    public static Set<String> tableNames() {
        return LIST_SQL.keySet();
    }

    public boolean isAllowedTable(String table) {
        return table != null && LIST_SQL.containsKey(table);
    }

    public long count(String table) throws SQLException {
        String sql = COUNT_SQL.get(table);
        if (sql == null) {
            throw new IllegalArgumentException("Unknown table");
        }
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<Map<String, Object>> listRows(String table, int limit) throws SQLException {
        String sql = LIST_SQL.get(table);
        if (sql == null) {
            throw new IllegalArgumentException("Unknown table");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        }
        return rows;
    }

    public Map<String, Object> tableBundle(String table, int limit) throws SQLException {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("table", table);
        bundle.put("count", count(table));
        bundle.put("limit", limit);
        bundle.put("rows", listRows(table, limit));
        return bundle;
    }

    public Map<String, Object> allTables(int limitPerTable, boolean includeAdmins) throws SQLException {
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String name : LIST_SQL.keySet()) {
            if (!includeAdmins && "admins".equals(name)) {
                continue;
            }
            tables.put(name, tableBundle(name, limitPerTable));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("database", "narainson_db");
        out.put("limit_per_table", limitPerTable);
        out.put("tables", tables);
        return out;
    }

    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        int cols = meta.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            String col = meta.getColumnLabel(i);
            Object val = rs.getObject(i);
            if (SENSITIVE_COLUMNS.contains(col)) {
                row.put(col, val == null ? null : "[redacted]");
                continue;
            }
            row.put(col, normalizeValue(val));
        }
        return row;
    }

    private static Object normalizeValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (val instanceof Date d) {
            return d.toLocalDate().toString();
        }
        if (val instanceof Time t) {
            return t.toLocalTime().toString();
        }
        if (val instanceof byte[] bytes) {
            return "[binary:" + bytes.length + " bytes]";
        }
        return val;
    }

    public static List<String> orderedTableLabels() {
        return new ArrayList<>(new LinkedHashSet<>(LIST_SQL.keySet()));
    }
}

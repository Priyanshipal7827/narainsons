package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.narainsons.util.DBConnection;

public class EmiCollectionSettingsDAO {

    /** Keeps one active row, so the app always gets a single set of payment details. */
    public void save(String payeeName, String upiId, String qrImageUrl, String bankName, String bankAccountName,
            String bankAccountNumber, String ifsc, String note) throws SQLException {
        DBConnection.inTransaction(() -> {
            try (Connection conn = DBConnection.getConnection();
                    PreparedStatement off = conn.prepareStatement("UPDATE emi_collection_settings SET is_active=0");
                    PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO emi_collection_settings (payee_name, upi_id, qr_image_url, bank_name,
                                bank_account_name, bank_account_number, ifsc_code, payment_note, is_active)
                            VALUES (?,?,?,?,?,?,?,?,1)
                            """)) {
                off.executeUpdate();
                ins.setString(1, payeeName);
                ins.setString(2, upiId);
                ins.setString(3, qrImageUrl);
                ins.setString(4, bankName);
                ins.setString(5, bankAccountName);
                ins.setString(6, bankAccountNumber);
                ins.setString(7, ifsc);
                ins.setString(8, note);
                ins.executeUpdate();
            }
            return null;
        });
    }

    public Optional<Map<String, Object>> findActive() throws SQLException {
        String sql = "SELECT * FROM emi_collection_settings WHERE is_active=1 ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("payee_name", rs.getString("payee_name"));
                m.put("upi_id", rs.getString("upi_id"));
                m.put("qr_image_url", rs.getString("qr_image_url"));
                m.put("bank_name", rs.getString("bank_name"));
                m.put("bank_account_name", rs.getString("bank_account_name"));
                m.put("bank_account_number", rs.getString("bank_account_number"));
                m.put("ifsc_code", rs.getString("ifsc_code"));
                m.put("payment_note", rs.getString("payment_note"));
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}

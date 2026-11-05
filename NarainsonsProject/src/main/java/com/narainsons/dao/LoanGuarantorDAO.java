package com.narainsons.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.narainsons.model.LoanGuarantor;
import com.narainsons.util.DBConnection;

public class LoanGuarantorDAO {

    public void insertAll(long loanApplicationId, List<LoanGuarantor> guarantors) throws SQLException {
        String sql = """
                INSERT INTO loan_guarantors (
                    loan_application_id, guarantor_index, full_name, mobile_number, relation,
                    address, aadhaar_last4, pan_last4
                ) VALUES (?,?,?,?,?,?,?,?)
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (LoanGuarantor g : guarantors) {
                ps.setLong(1, loanApplicationId);
                ps.setInt(2, g.getGuarantorIndex());
                ps.setString(3, g.getFullName());
                ps.setString(4, g.getMobileNumber());
                ps.setString(5, g.getRelation());
                ps.setString(6, g.getAddress());
                ps.setString(7, g.getAadhaarLast4());
                ps.setString(8, g.getPanLast4());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<LoanGuarantor> findByLoanApplicationId(long loanApplicationId) throws SQLException {
        String sql = """
                SELECT * FROM loan_guarantors WHERE loan_application_id = ?
                ORDER BY guarantor_index ASC
                """;
        List<LoanGuarantor> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, loanApplicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private static LoanGuarantor mapRow(ResultSet rs) throws SQLException {
        LoanGuarantor g = new LoanGuarantor();
        g.setId(rs.getLong("id"));
        g.setLoanApplicationId(rs.getLong("loan_application_id"));
        g.setGuarantorIndex(rs.getInt("guarantor_index"));
        g.setFullName(rs.getString("full_name"));
        g.setMobileNumber(rs.getString("mobile_number"));
        g.setRelation(rs.getString("relation"));
        g.setAddress(rs.getString("address"));
        g.setAadhaarLast4(rs.getString("aadhaar_last4"));
        g.setPanLast4(rs.getString("pan_last4"));
        Timestamp ts = rs.getTimestamp("created_at");
        g.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        return g;
    }
}

package com.narainsons.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.dao.LoanApplicationDAO.AdminLoanRow;
import com.narainsons.model.LoanApplication;

public class AdminLoanApplicationService {

    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();

    public Map<String, Object> listApplications(String status, String search, int limit, int offset)
            throws SQLException {
        int cappedLimit = Math.min(Math.max(limit, 1), 500);
        int safeOffset = Math.max(offset, 0);
        List<AdminLoanRow> rows = loanDAO.listForAdmin(status, search, cappedLimit, safeOffset);
        int total = loanDAO.countForAdmin(status, search);
        Map<String, Object> data = new HashMap<>();
        data.put("applications", rows.stream().map(this::toAdminMap).collect(Collectors.toList()));
        data.put("total", total);
        data.put("limit", cappedLimit);
        data.put("offset", safeOffset);
        data.put("stats", Map.of(
                "total", loanDAO.countAll(),
                "pending", loanDAO.countByStatus("pending"),
                "approved", loanDAO.countByStatus("approved"),
                "disbursed", loanDAO.countByStatus("disbursed"),
                "closed", loanDAO.countByStatus("closed"),
                "rejected", loanDAO.countByStatus("rejected")));
        data.put("source", Map.of("database", "narainson_db", "table", "loan_applications"));
        return data;
    }

    private Map<String, Object> toAdminMap(AdminLoanRow row) {
        LoanApplication a = row.application;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("user_id", a.getUserId());
        m.put("application_code", a.getApplicationCode());
        m.put("requested_amount", a.getRequestedAmount());
        m.put("tenure_months", a.getTenureMonths());
        m.put("purpose", a.getPurpose());
        m.put("purpose_detail", a.getPurposeDetail());
        m.put("monthly_income", a.getMonthlyIncome());
        m.put("employment_type", a.getEmploymentType());
        m.put("company_name", a.getCompanyName());
        m.put("bank_name", a.getBankName());
        m.put("ifsc_code", a.getIfscCode());
        m.put("bank_account_last4", a.getBankAccountLast4());
        m.put("status", a.getStatus());
        m.put("interest_rate_annual", a.getInterestRateAnnual());
        m.put("processing_fee", a.getProcessingFee());
        m.put("total_payable", a.getTotalPayable());
        m.put("emi_amount", a.getEmiAmount());
        m.put("disbursed_at", a.getDisbursedAt());
        m.put("disbursement_reference", a.getDisbursementReference());
        m.put("closed_at", a.getClosedAt());
        m.put("status_message", a.getStatusMessage());
        m.put("admin_note", a.getAdminNote());
        m.put("reviewed_at", a.getReviewedAt());
        m.put("created_at", a.getCreatedAt());
        m.put("updated_at", a.getUpdatedAt());
        m.put("user_name", row.userName);
        m.put("user_email", row.userEmail);
        m.put("user_mobile", row.userMobile);
        return m;
    }
}

package com.narainsons.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.narainsons.dao.AdminDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.model.Admin;
import com.narainsons.model.LoanApplication;

public class AdminDashboardService {

    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();
    private final AdminDAO adminDAO = new AdminDAO();
    private final AdminLoanEmiService loanEmiService = new AdminLoanEmiService();

    public Map<String, Object> dashboard(Admin admin) throws SQLException {
        List<LoanApplication> pendingLoans = loanDAO.listByStatus("pending", 50);
        Map<String, Object> data = new HashMap<>();
        data.put("admin", AdminAuthService.sanitize(admin));
        data.put("pending_loan_count", pendingLoans.size());
        data.put("pending_loans", pendingLoans.stream().map(this::loanBrief).collect(Collectors.toList()));
        var pendingEmi = loanEmiService.listPendingPayments(50);
        data.put("pending_emi_payments", pendingEmi);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_applications", loanDAO.countAll());
        stats.put("pending_loans", loanDAO.countByStatus("pending"));
        stats.put("approved_loans", loanDAO.countByStatus("approved"));
        stats.put("rejected_loans", loanDAO.countByStatus("rejected"));
        stats.put("pending_emi", pendingEmi.size());
        stats.put("active_admins", adminDAO.countActive());
        data.put("stats", stats);
        return data;
    }

    public List<Map<String, Object>> listPendingLoans(int limit) throws SQLException {
        return loanDAO.listByStatus("pending", limit).stream().map(this::loanBrief).collect(Collectors.toList());
    }

    private Map<String, Object> loanBrief(LoanApplication a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("application_code", a.getApplicationCode());
        m.put("user_id", a.getUserId());
        m.put("requested_amount", a.getRequestedAmount());
        m.put("tenure_months", a.getTenureMonths());
        m.put("purpose", a.getPurpose());
        m.put("status", a.getStatus());
        m.put("created_at", a.getCreatedAt());
        return m;
    }
}

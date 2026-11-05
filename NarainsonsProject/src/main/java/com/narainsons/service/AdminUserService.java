package com.narainsons.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.narainsons.dao.DeviceDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.util.DBConnection;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.LoanApplication;
import com.narainsons.model.User;
import com.narainsons.util.ApiException;
import com.narainsons.util.ServletUtil;

public class AdminUserService {

    private final UserDAO userDAO = new UserDAO();
    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();

    public Map<String, Object> listUsers(String search, String accountStatus, String kycStatus, int limit, int offset)
            throws SQLException {
        List<User> users = userDAO.searchForAdmin(search, accountStatus, kycStatus, limit, offset);
        Map<String, Object> data = new HashMap<>();
        data.put("users", users.stream().map(this::userForList).collect(Collectors.toList()));
        data.put("stats", userDAO.adminUserStats());
        data.put("source", Map.of("database", "narainson_db", "table", "users"));
        data.put("limit", limit);
        data.put("offset", offset);
        return data;
    }

    public Map<String, Object> getUser(long id) throws SQLException {
        User user = userDAO.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        List<LoanApplication> loans = loanDAO.listByUser(id, 20);
        Map<String, Object> data = new HashMap<>();
        data.put("user", userForDetail(user));
        data.put("loan_applications", loans.stream().map(this::loanBrief).collect(Collectors.toList()));
        data.put("source", Map.of("database", "narainson_db", "table", "users"));
        return data;
    }

    public Map<String, Object> deleteUser(long id) throws SQLException {
        if (!userDAO.softDelete(id)) {
            throw ApiException.notFound("User not found or already deleted");
        }
        new TokenService().revokeAll(id);
        new DeviceDAO().removeAllForUser(id);
        return Map.of("deleted", true, "user_id", id);
    }

    /** action: verify or reject. Verifying also marks the bank details verified. */
    public Map<String, Object> reviewKyc(long id, String action, String reason) throws SQLException {
        User user = userDAO.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!"submitted".equalsIgnoreCase(user.getKycStatus())) {
            throw new IllegalArgumentException("Only submitted KYC can be reviewed (current: " + user.getKycStatus() + ")");
        }
        boolean verify = "verify".equalsIgnoreCase(action);
        if (!verify && !"reject".equalsIgnoreCase(action)) {
            throw new IllegalArgumentException("action must be verify or reject");
        }
        if (!verify && ServletUtil.isBlank(reason)) {
            throw new IllegalArgumentException("reason is required when rejecting KYC");
        }
        String status = verify ? "verified" : "rejected";
        DBConnection.inTransaction(() -> {
            userDAO.updateKyc(id, status, verify ? null : reason.trim(), verify ? "verified" : "failed");
            new NotificationService().notifyUser(id, "kyc",
                    verify ? "KYC verified" : "KYC rejected",
                    verify ? "Aapka KYC verify ho gaya hai."
                            : "Aapka KYC reject ho gaya: " + reason.trim() + ". Sahi details ke saath dobara submit karein.",
                    Map.of("kyc_status", status));
            return null;
        });
        return Map.of("user_id", id, "kyc_status", status);
    }

    public Map<String, Object> setUserActive(long id, boolean active) throws SQLException {
        if (userDAO.findById(id).isEmpty()) {
            throw ApiException.notFound("User not found");
        }
        String status = active ? "active" : "inactive";
        if (!userDAO.updateAccountStatus(id, status)) {
            throw new IllegalArgumentException("Could not update user status");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", id);
        data.put("account_status", status);
        data.put("active", active);
        return data;
    }

    private Map<String, Object> userForList(User u) {
        User safe = ServletUtil.sanitizeUser(u);
        Map<String, Object> m = new HashMap<>();
        m.put("id", safe.getId());
        m.put("customer_code", safe.getCustomerCode());
        m.put("name", safe.getName());
        m.put("mobile_number", safe.getMobileNumber());
        m.put("email", safe.getEmail());
        m.put("kyc_status", safe.getKycStatus());
        m.put("account_status", safe.getAccountStatus());
        m.put("onboarding_step", safe.getOnboardingStep());
        m.put("employment_type", safe.getEmploymentType());
        m.put("created_at", safe.getCreatedAt());
        m.put("last_login", safe.getLastLogin());
        return m;
    }

    private User userForDetail(User u) {
        User safe = ServletUtil.sanitizeUser(u);
        safe.setContactsData(truncateJson(safe.getContactsData(), 2000));
        safe.setGalleryData(truncateJson(safe.getGalleryData(), 2000));
        safe.setFcmToken(null);
        safe.setSessionToken(null);
        return safe;
    }

    private Map<String, Object> loanBrief(LoanApplication a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("application_code", a.getApplicationCode());
        m.put("requested_amount", a.getRequestedAmount());
        m.put("tenure_months", a.getTenureMonths());
        m.put("purpose", a.getPurpose());
        m.put("status", a.getStatus());
        m.put("created_at", a.getCreatedAt());
        return m;
    }

    private static String truncateJson(String raw, int maxLen) {
        if (raw == null || raw.length() <= maxLen) {
            return raw;
        }
        return raw.substring(0, maxLen) + "… (truncated)";
    }
}

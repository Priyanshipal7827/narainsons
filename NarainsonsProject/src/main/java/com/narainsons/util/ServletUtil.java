package com.narainsons.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DataTruncation;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.narainsons.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class ServletUtil {

    private static final Logger LOG = Logger.getLogger(ServletUtil.class.getName());

    private ServletUtil() {
    }

    public static void prepareJsonResponse(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
    }

    public static void writeJson(HttpServletResponse response, int status, ApiResponse body) throws IOException {
        response.setStatus(status);
        if (!body.isSuccess() && body.getErrorCode() == null) {
            body.setErrorCode(ApiResponse.codeForStatus(status));
        }
        response.getWriter().write(GsonProvider.toJson(body));
    }

    /** ApiException carries its own status; any other IllegalArgumentException is bad input. */
    public static void writeError(HttpServletResponse response, IllegalArgumentException e) throws IOException {
        prepareJsonResponse(response);
        int status = e instanceof ApiException api ? api.status() : HttpServletResponse.SC_BAD_REQUEST;
        String message = e instanceof NumberFormatException ? "Invalid number in request" : e.getMessage();
        ApiResponse body = ApiResponse.error(message);
        if (e instanceof ApiException api) {
            body.setErrorCode(api.code());
        }
        writeJson(response, status, body);
    }

    /** Logs the real error and tells the client only what it can act on. */
    public static void writeDbError(HttpServletResponse response, SQLException e) throws IOException {
        prepareJsonResponse(response);
        if (e instanceof SQLIntegrityConstraintViolationException) {
            writeJson(response, HttpServletResponse.SC_CONFLICT,
                    ApiResponse.error("This value already exists or is linked to other records"));
            return;
        }
        if (e instanceof SQLDataException || e instanceof DataTruncation) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, ApiResponse.error("A field has an invalid value"));
            return;
        }
        LOG.log(Level.SEVERE, "Database error", e);
        writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                ApiResponse.error("Something went wrong. Please try again."));
    }

    /** The app's view of a user: an explicit list, so new columns are never exposed by accident. */
    public static Map<String, Object> userView(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("customer_code", u.getCustomerCode());
        m.put("name", u.getName());
        m.put("first_name", u.getFirstName());
        m.put("last_name", u.getLastName());
        m.put("mobile_number", u.getMobileNumber());
        m.put("alternate_mobile_number", u.getAlternateMobileNumber());
        m.put("email", u.getEmail());
        m.put("mobile_verified", u.getMobileVerifiedAt() != null);
        m.put("email_verified", u.getEmailVerifiedAt() != null);
        m.put("has_password", !isBlank(u.getPasswordHash()));
        m.put("gender", u.getGender());
        m.put("date_of_birth", u.getDateOfBirth());
        m.put("age", u.getAge());
        m.put("marital_status", u.getMaritalStatus());
        m.put("nationality", u.getNationality());
        m.put("profile_image", u.getProfileImage());
        m.put("kyc_status", u.getKycStatus());
        m.put("kyc_verified_at", u.getKycVerifiedAt());
        m.put("kyc_rejection_reason", u.getKycRejectionReason());
        m.put("aadhaar_last4", u.getAadhaarLast4());
        m.put("pan_last4", u.getPanLast4());
        m.put("aadhaar_document", u.getAadhaarDocument());
        m.put("pan_document", u.getPanDocument());
        m.put("current_address", u.getCurrentAddress());
        m.put("current_city", u.getCurrentCity());
        m.put("current_state", u.getCurrentState());
        m.put("current_pincode", u.getCurrentPincode());
        m.put("current_country", u.getCurrentCountry());
        m.put("permanent_address", u.getPermanentAddress());
        m.put("permanent_city", u.getPermanentCity());
        m.put("permanent_state", u.getPermanentState());
        m.put("permanent_pincode", u.getPermanentPincode());
        m.put("permanent_country", u.getPermanentCountry());
        m.put("same_as_current_address", u.getSameAsCurrentAddress());
        m.put("employment_type", u.getEmploymentType());
        m.put("company_name", u.getCompanyName());
        m.put("designation", u.getDesignation());
        m.put("work_experience_months", u.getWorkExperienceMonths());
        m.put("monthly_income", u.getMonthlyIncome());
        m.put("annual_income", u.getAnnualIncome());
        m.put("income_source", u.getIncomeSource());
        m.put("bank_name", u.getBankName());
        m.put("bank_account_last4", u.getBankAccountLast4());
        m.put("ifsc_code", u.getIfscCode());
        m.put("bank_verification_status", u.getBankVerificationStatus());
        m.put("account_status", u.getAccountStatus());
        m.put("notification_enable", u.getNotificationEnable());
        m.put("onboarding_step", u.getOnboardingStep());
        m.put("privacy_consent", u.getPrivacyConsent());
        m.put("terms_accepted", u.getTermsAccepted());
        m.put("kyc_consent", u.getKycConsent());
        m.put("last_login", u.getLastLogin());
        m.put("created_at", u.getCreatedAt());
        return m;
    }

    public static User sanitizeUser(User user) {
        User safe = GsonProvider.fromJson(GsonProvider.toJson(user), User.class);
        safe.setPasswordHash(null);
        safe.setOtpHash(null);
        safe.setAadhaarEncrypted(null);
        safe.setPanEncrypted(null);
        safe.setBankAccountEncrypted(null);
        safe.setPassword(null);
        return safe;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String header = request.getHeader("X-Access-Token");
        return header == null ? null : header.trim();
    }

    public static String[] splitPathInfo(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            return new String[0];
        }
        String trimmed = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.split("/");
    }

    public static int parseIntParam(String raw, int defaultValue, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Long readUserIdFromRequest(HttpServletRequest request) {
        Object value = request.getAttribute("authenticatedUserId");
        if (value instanceof Long id) {
            return id;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}

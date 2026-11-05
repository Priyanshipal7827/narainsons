package com.narainsons.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.narainsons.model.User;
import com.narainsons.util.DBConnection;

public class UserDAO {

    private static final String SELECT_BASE = """
            SELECT * FROM users WHERE deleted_at IS NULL
            """;

    public long create(User user) throws SQLException {
        String sql = """
                INSERT INTO users (
                    customer_code, name, first_name, last_name, mobile_number, alternate_mobile_number, email,
                    gender, date_of_birth, age, marital_status, nationality, profile_image,
                    aadhaar_encrypted, aadhaar_last4, pan_encrypted, pan_last4, aadhaar_document, pan_document,
                    kyc_status, kyc_verified_at, kyc_rejection_reason,
                    current_address, current_city, current_state, current_pincode, current_country,
                    permanent_address, permanent_city, permanent_state, permanent_pincode, permanent_country,
                    same_as_current_address, employment_type, company_name, designation, work_experience_months,
                    monthly_income, annual_income, income_source, bank_name, bank_account_encrypted,
                    bank_account_last4, ifsc_code, bank_verification_status, bank_verified_at,
                    password_hash, otp_hash, otp_expires_at, otp_verified_at, email_verified_at, mobile_verified_at,
                    login_attempts, locked_until, last_login, last_logout_at, account_status,
                    fcm_token, fcm_token_updated_at, notification_enable, notification_disable_at,
                    device_id, device_type, device_name, device_model, os_version, app_version, time_zone,
                    is_login, is_app_installed, application_install_time, application_uninstall_time,
                    latitude, longitude, privacy_consent, privacy_consent_at, terms_accepted, terms_accepted_at,
                    kyc_consent, kyc_consent_at, onboarding_step
                ) VALUES (
                    ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?
                )
                """;

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindUserForInsert(ps, user);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("User insert ke baad id generate nahi hui");
    }

    public boolean update(User user) throws SQLException {
        String sql = """
                UPDATE users SET
                    customer_code=?, name=?, first_name=?, last_name=?, mobile_number=?, alternate_mobile_number=?,
                    email=?, gender=?, date_of_birth=?, age=?, marital_status=?, nationality=?, profile_image=?,
                    aadhaar_encrypted=?, aadhaar_last4=?, pan_encrypted=?, pan_last4=?, aadhaar_document=?, pan_document=?,
                    kyc_status=?, kyc_verified_at=?, kyc_rejection_reason=?,
                    current_address=?, current_city=?, current_state=?, current_pincode=?, current_country=?,
                    permanent_address=?, permanent_city=?, permanent_state=?, permanent_pincode=?, permanent_country=?,
                    same_as_current_address=?, employment_type=?, company_name=?, designation=?, work_experience_months=?,
                    monthly_income=?, annual_income=?, income_source=?, bank_name=?, bank_account_encrypted=?,
                    bank_account_last4=?, ifsc_code=?, bank_verification_status=?, bank_verified_at=?,
                    password_hash=?, otp_hash=?, otp_expires_at=?, otp_verified_at=?, email_verified_at=?, mobile_verified_at=?,
                    login_attempts=?, locked_until=?, last_login=?, last_logout_at=?, account_status=?,
                    fcm_token=?, fcm_token_updated_at=?, notification_enable=?, notification_disable_at=?,
                    device_id=?, device_type=?, device_name=?, device_model=?, os_version=?, app_version=?, time_zone=?,
                    is_login=?, is_app_installed=?, application_install_time=?, application_uninstall_time=?,
                    latitude=?, longitude=?, privacy_consent=?, privacy_consent_at=?, terms_accepted=?, terms_accepted_at=?,
                    kyc_consent=?, kyc_consent_at=?, onboarding_step=?
                WHERE id=? AND deleted_at IS NULL
                """;

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = bindUserForInsert(ps, user);
            ps.setLong(i, user.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        String sql = SELECT_BASE + " AND id = ?";
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

    public Optional<User> findByMobile(String mobile) throws SQLException {
        String sql = SELECT_BASE + " AND mobile_number = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mobile);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = SELECT_BASE + " AND email = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByCustomerCode(String customerCode) throws SQLException {
        String sql = SELECT_BASE + " AND customer_code = ?";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> findAll(int limit, int offset) throws SQLException {
        String sql = SELECT_BASE + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public List<User> findByFilters(String kycStatus, String accountStatus, int limit, int offset)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        if (kycStatus != null && !kycStatus.isBlank()) {
            sql.append(" AND kyc_status = ?");
            params.add(kycStatus);
        }
        if (accountStatus != null && !accountStatus.isBlank()) {
            sql.append(" AND account_status = ?");
            params.add(accountStatus);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (param instanceof Integer n) {
                    ps.setInt(i + 1, n);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public boolean softDelete(long id) throws SQLException {
        String sql = "UPDATE users SET deleted_at = NOW(), account_status='inactive', token_version=token_version+1, is_login=FALSE WHERE id = ? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateAccountStatus(long id, String accountStatus) throws SQLException {
        String sql = "UPDATE users SET account_status=? WHERE id=? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountStatus);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Long> adminUserStats() throws SQLException {
        Map<String, Long> stats = new HashMap<>();
        String base = "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL";
        stats.put("total_users", countSql(base));
        stats.put("active_users", countSql(base + " AND account_status='active'"));
        stats.put("inactive_users", countSql(base + " AND account_status <> 'active'"));
        stats.put("kyc_pending", countSql(base + " AND (kyc_status IS NULL OR kyc_status IN ('pending','submitted'))"));
        stats.put("kyc_verified", countSql(base + " AND kyc_status='verified'"));
        return stats;
    }

    private long countSql(String sql) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<User> searchForAdmin(String query, String accountStatus, String kycStatus, int limit, int offset)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();
            String like = "%" + trimmed + "%";
            String digits = trimmed.replaceAll("\\D", "");
            if (trimmed.matches("\\d+")) {
                sql.append("""
                         AND (
                           id = ? OR CAST(id AS CHAR) LIKE ?
                           OR name LIKE ? OR mobile_number LIKE ? OR email LIKE ?
                           OR customer_code LIKE ?
                           OR REPLACE(mobile_number, ' ', '') LIKE ?
                         )
                        """);
                params.add(Long.parseLong(trimmed));
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add("%" + digits + "%");
            } else {
                sql.append("""
                         AND (
                           name LIKE ? OR mobile_number LIKE ? OR email LIKE ?
                           OR customer_code LIKE ? OR CAST(id AS CHAR) LIKE ?
                           OR REPLACE(mobile_number, ' ', '') LIKE ?
                         )
                        """);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
                params.add("%" + digits + "%");
            }
        }
        if (accountStatus != null && !accountStatus.isBlank()) {
            sql.append(" AND account_status = ?");
            params.add(accountStatus);
        }
        if (kycStatus != null && !kycStatus.isBlank()) {
            sql.append(" AND kyc_status = ?");
            params.add(kycStatus);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (param instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else if (param instanceof Long l) {
                    ps.setLong(i + 1, l);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public User createMinimalByMobile(String mobile) throws SQLException {
        User user = new User();
        user.setName("New User");
        user.setMobileNumber(mobile);
        user.setOnboardingStep(0);
        UserDefaultsLite.apply(user);
        long id = create(user);
        return findById(id).orElseThrow(() -> new SQLException("User create fail"));
    }

    public User createMinimalByEmail(String email) throws SQLException {
        User user = new User();
        user.setName("New User");
        user.setEmail(email);
        user.setMobileNumber("TEMP" + System.currentTimeMillis());
        user.setOnboardingStep(0);
        UserDefaultsLite.apply(user);
        long id = create(user);
        return findById(id).orElseThrow(() -> new SQLException("User create fail"));
    }

    public void saveOtp(long userId, String otpHash, LocalDateTime expiresAt) throws SQLException {
        String sql = "UPDATE users SET otp_hash=?, otp_expires_at=?, otp_attempts=0 WHERE id=? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, otpHash);
            setDateTime(ps, 2, expiresAt);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public void markMobileVerified(long userId) throws SQLException {
        String sql = """
                UPDATE users SET mobile_verified_at=NOW(), otp_hash=NULL, otp_expires_at=NULL
                WHERE id=? AND deleted_at IS NULL
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void markEmailVerified(long userId) throws SQLException {
        String sql = """
                UPDATE users SET email_verified_at=NOW(), otp_hash=NULL, otp_expires_at=NULL
                WHERE id=? AND deleted_at IS NULL
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void updatePasswordHash(long userId, String passwordHash) throws SQLException {
        String sql = """
                UPDATE users SET password_hash=?, otp_hash=NULL, otp_expires_at=NULL, otp_attempts=0,
                    token_version=token_version+1, is_login=FALSE,
                    login_attempts=0, locked_until=NULL, updated_at=NOW()
                WHERE id=? AND deleted_at IS NULL
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public void markLogin(long userId) throws SQLException {
        String sql = "UPDATE users SET is_login=TRUE, last_login=NOW() WHERE id=? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /** Ends every access token already issued to this user. */
    public void bumpTokenVersion(long userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE users SET token_version=token_version+1, is_login=FALSE WHERE id=?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void updateNotificationEnabled(long userId, boolean enabled) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET notification_enable=?, notification_disable_at=IF(?, NULL, NOW()) WHERE id=?")) {
            ps.setBoolean(1, enabled);
            ps.setBoolean(2, enabled);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    public void updateKyc(long userId, String kycStatus, String rejectionReason, String bankStatus) throws SQLException {
        String sql = """
                UPDATE users SET kyc_status=?, kyc_rejection_reason=?,
                    kyc_verified_at=IF(?='verified', NOW(), NULL),
                    bank_verification_status=?, bank_verified_at=IF(?='verified', NOW(), NULL)
                WHERE id=? AND deleted_at IS NULL
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kycStatus);
            ps.setString(2, rejectionReason);
            ps.setString(3, kycStatus);
            ps.setString(4, bankStatus);
            ps.setString(5, bankStatus);
            ps.setLong(6, userId);
            ps.executeUpdate();
        }
    }

    /** Serialises concurrent writes for one user; call inside DBConnection.inTransaction. */
    public void lockForUpdate(long userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
            ps.setLong(1, userId);
            ps.executeQuery().close();
        }
    }

    public List<Long> activeUserIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE deleted_at IS NULL AND account_status='active'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    public boolean isMobileOfDeletedUser(String mobile) throws SQLException {
        return existsDeleted("mobile_number", mobile);
    }

    public boolean isEmailOfDeletedUser(String email) throws SQLException {
        return existsDeleted("email", email);
    }

    private boolean existsDeleted(String column, String value) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE " + column + "=? AND deleted_at IS NOT NULL LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Counts a wrong OTP; the fifth one drops the OTP so a new one has to be requested. */
    public void recordOtpFailure(long userId) throws SQLException {
        String sql = """
                UPDATE users SET otp_attempts = otp_attempts + 1,
                    otp_hash = IF(otp_attempts >= 5, NULL, otp_hash),
                    otp_expires_at = IF(otp_attempts >= 5, NULL, otp_expires_at)
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void recordLoginFailure(long userId, int maxFailures, LocalDateTime lockUntil) throws SQLException {
        String sql = """
                UPDATE users SET login_attempts = COALESCE(login_attempts, 0) + 1,
                    locked_until = IF(login_attempts >= ?, ?, locked_until),
                    login_attempts = IF(login_attempts >= ?, 0, login_attempts)
                WHERE id=?
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxFailures);
            setDateTime(ps, 2, lockUntil);
            ps.setInt(3, maxFailures);
            ps.setLong(4, userId);
            ps.executeUpdate();
        }
    }

    public void clearLoginFailures(long userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET login_attempts=0, locked_until=NULL WHERE id=?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void updateSyncPayload(long userId, String contactsJson, String galleryJson) throws SQLException {
        String sql = "UPDATE users SET contacts_data=?, gallery_data=? WHERE id=? AND deleted_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contactsJson);
            ps.setString(2, galleryJson);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    private int bindUserForInsert(PreparedStatement ps, User u) throws SQLException {
        int i = 1;
        ps.setString(i++, u.getCustomerCode());
        ps.setString(i++, u.getName());
        ps.setString(i++, u.getFirstName());
        ps.setString(i++, u.getLastName());
        ps.setString(i++, u.getMobileNumber());
        ps.setString(i++, u.getAlternateMobileNumber());
        ps.setString(i++, u.getEmail());
        ps.setString(i++, u.getGender());
        setDate(ps, i++, u.getDateOfBirth());
        setInteger(ps, i++, u.getAge());
        ps.setString(i++, u.getMaritalStatus());
        ps.setString(i++, u.getNationality());
        ps.setString(i++, u.getProfileImage());
        ps.setString(i++, u.getAadhaarEncrypted());
        ps.setString(i++, u.getAadhaarLast4());
        ps.setString(i++, u.getPanEncrypted());
        ps.setString(i++, u.getPanLast4());
        ps.setString(i++, u.getAadhaarDocument());
        ps.setString(i++, u.getPanDocument());
        ps.setString(i++, u.getKycStatus());
        setDateTime(ps, i++, u.getKycVerifiedAt());
        ps.setString(i++, u.getKycRejectionReason());
        ps.setString(i++, u.getCurrentAddress());
        ps.setString(i++, u.getCurrentCity());
        ps.setString(i++, u.getCurrentState());
        ps.setString(i++, u.getCurrentPincode());
        ps.setString(i++, u.getCurrentCountry());
        ps.setString(i++, u.getPermanentAddress());
        ps.setString(i++, u.getPermanentCity());
        ps.setString(i++, u.getPermanentState());
        ps.setString(i++, u.getPermanentPincode());
        ps.setString(i++, u.getPermanentCountry());
        setBoolean(ps, i++, u.getSameAsCurrentAddress());
        ps.setString(i++, u.getEmploymentType());
        ps.setString(i++, u.getCompanyName());
        ps.setString(i++, u.getDesignation());
        setInteger(ps, i++, u.getWorkExperienceMonths());
        setBigDecimal(ps, i++, u.getMonthlyIncome());
        setBigDecimal(ps, i++, u.getAnnualIncome());
        ps.setString(i++, u.getIncomeSource());
        ps.setString(i++, u.getBankName());
        ps.setString(i++, u.getBankAccountEncrypted());
        ps.setString(i++, u.getBankAccountLast4());
        ps.setString(i++, u.getIfscCode());
        ps.setString(i++, u.getBankVerificationStatus());
        setDateTime(ps, i++, u.getBankVerifiedAt());
        ps.setString(i++, u.getPasswordHash());
        ps.setString(i++, u.getOtpHash());
        setDateTime(ps, i++, u.getOtpExpiresAt());
        setDateTime(ps, i++, u.getOtpVerifiedAt());
        setDateTime(ps, i++, u.getEmailVerifiedAt());
        setDateTime(ps, i++, u.getMobileVerifiedAt());
        setInteger(ps, i++, u.getLoginAttempts());
        setDateTime(ps, i++, u.getLockedUntil());
        setDateTime(ps, i++, u.getLastLogin());
        setDateTime(ps, i++, u.getLastLogoutAt());
        ps.setString(i++, u.getAccountStatus());
        ps.setString(i++, u.getFcmToken());
        setDateTime(ps, i++, u.getFcmTokenUpdatedAt());
        setBoolean(ps, i++, u.getNotificationEnable());
        setDateTime(ps, i++, u.getNotificationDisableAt());
        ps.setString(i++, u.getDeviceId());
        ps.setString(i++, u.getDeviceType());
        ps.setString(i++, u.getDeviceName());
        ps.setString(i++, u.getDeviceModel());
        ps.setString(i++, u.getOsVersion());
        ps.setString(i++, u.getAppVersion());
        ps.setString(i++, u.getTimeZone());
        setBoolean(ps, i++, u.getIsLogin());
        setBoolean(ps, i++, u.getIsAppInstalled());
        setDateTime(ps, i++, u.getApplicationInstallTime());
        setDateTime(ps, i++, u.getApplicationUninstallTime());
        setBigDecimal(ps, i++, u.getLatitude());
        setBigDecimal(ps, i++, u.getLongitude());
        setBoolean(ps, i++, u.getPrivacyConsent());
        setDateTime(ps, i++, u.getPrivacyConsentAt());
        setBoolean(ps, i++, u.getTermsAccepted());
        setDateTime(ps, i++, u.getTermsAcceptedAt());
        setBoolean(ps, i++, u.getKycConsent());
        setDateTime(ps, i++, u.getKycConsentAt());
        setInteger(ps, i++, u.getOnboardingStep() == null ? 0 : u.getOnboardingStep());
        return i;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setCustomerCode(rs.getString("customer_code"));
        u.setName(rs.getString("name"));
        u.setFirstName(rs.getString("first_name"));
        u.setLastName(rs.getString("last_name"));
        u.setMobileNumber(rs.getString("mobile_number"));
        u.setAlternateMobileNumber(rs.getString("alternate_mobile_number"));
        u.setEmail(rs.getString("email"));
        u.setGender(rs.getString("gender"));
        u.setDateOfBirth(getLocalDate(rs, "date_of_birth"));
        u.setAge(getInteger(rs, "age"));
        u.setMaritalStatus(rs.getString("marital_status"));
        u.setNationality(rs.getString("nationality"));
        u.setProfileImage(rs.getString("profile_image"));
        u.setAadhaarEncrypted(rs.getString("aadhaar_encrypted"));
        u.setAadhaarLast4(rs.getString("aadhaar_last4"));
        u.setPanEncrypted(rs.getString("pan_encrypted"));
        u.setPanLast4(rs.getString("pan_last4"));
        u.setAadhaarDocument(rs.getString("aadhaar_document"));
        u.setPanDocument(rs.getString("pan_document"));
        u.setKycStatus(rs.getString("kyc_status"));
        u.setKycVerifiedAt(getLocalDateTime(rs, "kyc_verified_at"));
        u.setKycRejectionReason(rs.getString("kyc_rejection_reason"));
        u.setCurrentAddress(rs.getString("current_address"));
        u.setCurrentCity(rs.getString("current_city"));
        u.setCurrentState(rs.getString("current_state"));
        u.setCurrentPincode(rs.getString("current_pincode"));
        u.setCurrentCountry(rs.getString("current_country"));
        u.setPermanentAddress(rs.getString("permanent_address"));
        u.setPermanentCity(rs.getString("permanent_city"));
        u.setPermanentState(rs.getString("permanent_state"));
        u.setPermanentPincode(rs.getString("permanent_pincode"));
        u.setPermanentCountry(rs.getString("permanent_country"));
        u.setSameAsCurrentAddress(getBoolean(rs, "same_as_current_address"));
        u.setEmploymentType(rs.getString("employment_type"));
        u.setCompanyName(rs.getString("company_name"));
        u.setDesignation(rs.getString("designation"));
        u.setWorkExperienceMonths(getInteger(rs, "work_experience_months"));
        u.setMonthlyIncome(rs.getBigDecimal("monthly_income"));
        u.setAnnualIncome(rs.getBigDecimal("annual_income"));
        u.setIncomeSource(rs.getString("income_source"));
        u.setBankName(rs.getString("bank_name"));
        u.setBankAccountEncrypted(rs.getString("bank_account_encrypted"));
        u.setBankAccountLast4(rs.getString("bank_account_last4"));
        u.setIfscCode(rs.getString("ifsc_code"));
        u.setBankVerificationStatus(rs.getString("bank_verification_status"));
        u.setBankVerifiedAt(getLocalDateTime(rs, "bank_verified_at"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setOtpHash(rs.getString("otp_hash"));
        u.setOtpExpiresAt(getLocalDateTime(rs, "otp_expires_at"));
        u.setOtpVerifiedAt(getLocalDateTime(rs, "otp_verified_at"));
        u.setEmailVerifiedAt(getLocalDateTime(rs, "email_verified_at"));
        u.setMobileVerifiedAt(getLocalDateTime(rs, "mobile_verified_at"));
        u.setLoginAttempts(getInteger(rs, "login_attempts"));
        u.setLockedUntil(getLocalDateTime(rs, "locked_until"));
        u.setLastLogin(getLocalDateTime(rs, "last_login"));
        u.setLastLogoutAt(getLocalDateTime(rs, "last_logout_at"));
        u.setAccountStatus(rs.getString("account_status"));
        u.setFcmToken(rs.getString("fcm_token"));
        u.setFcmTokenUpdatedAt(getLocalDateTime(rs, "fcm_token_updated_at"));
        u.setNotificationEnable(getBoolean(rs, "notification_enable"));
        u.setNotificationDisableAt(getLocalDateTime(rs, "notification_disable_at"));
        u.setDeviceId(rs.getString("device_id"));
        u.setDeviceType(rs.getString("device_type"));
        u.setDeviceName(rs.getString("device_name"));
        u.setDeviceModel(rs.getString("device_model"));
        u.setOsVersion(rs.getString("os_version"));
        u.setAppVersion(rs.getString("app_version"));
        u.setTimeZone(rs.getString("time_zone"));
        u.setIsLogin(getBoolean(rs, "is_login"));
        u.setIsAppInstalled(getBoolean(rs, "is_app_installed"));
        u.setApplicationInstallTime(getLocalDateTime(rs, "application_install_time"));
        u.setApplicationUninstallTime(getLocalDateTime(rs, "application_uninstall_time"));
        u.setLatitude(rs.getBigDecimal("latitude"));
        u.setLongitude(rs.getBigDecimal("longitude"));
        u.setPrivacyConsent(getBoolean(rs, "privacy_consent"));
        u.setPrivacyConsentAt(getLocalDateTime(rs, "privacy_consent_at"));
        u.setTermsAccepted(getBoolean(rs, "terms_accepted"));
        u.setTermsAcceptedAt(getLocalDateTime(rs, "terms_accepted_at"));
        u.setKycConsent(getBoolean(rs, "kyc_consent"));
        u.setKycConsentAt(getLocalDateTime(rs, "kyc_consent_at"));
        u.setCreatedAt(getLocalDateTime(rs, "created_at"));
        u.setUpdatedAt(getLocalDateTime(rs, "updated_at"));
        u.setDeletedAt(getLocalDateTime(rs, "deleted_at"));
        mapOptionalColumn(rs, u, "onboarding_step", (user, val) -> user.setOnboardingStep((Integer) val));
        u.setTokenVersion(rs.getInt("token_version"));
        mapOptionalColumn(rs, u, "contacts_data", (user, val) -> user.setContactsData((String) val));
        mapOptionalColumn(rs, u, "gallery_data", (user, val) -> user.setGalleryData((String) val));
        return u;
    }

    @FunctionalInterface
    private interface UserColumnSetter {
        void apply(User user, Object value);
    }

    private static void mapOptionalColumn(ResultSet rs, User user, String column, UserColumnSetter setter) {
        try {
            Object value = switch (column) {
                case "onboarding_step" -> getInteger(rs, column);
                default -> rs.getString(column);
            };
            setter.apply(user, value);
        } catch (SQLException ignored) {
            // column migration abhi nahi chali
        }
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate date) throws SQLException {
        if (date == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    private static void setDateTime(PreparedStatement ps, int index, LocalDateTime dateTime) throws SQLException {
        if (dateTime == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(dateTime));
        }
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static void setBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        boolean v = rs.getBoolean(column);
        return rs.wasNull() ? null : v;
    }
}

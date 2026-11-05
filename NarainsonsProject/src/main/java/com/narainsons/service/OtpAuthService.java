package com.narainsons.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.util.ApiException;
import com.narainsons.util.JwtService;
import com.narainsons.util.OtpUtil;
import com.narainsons.util.PasswordUtil;
import com.narainsons.util.ServletUtil;
import com.narainsons.util.Validators;

public class OtpAuthService {

    private static final Logger LOG = Logger.getLogger(OtpAuthService.class.getName());
    private static final int OTP_MINUTES = 10;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;
    private static final String ACCOUNT_CLOSED = "This account was closed. Contact Narainsons support.";

    private final UserDAO userDAO = new UserDAO();
    private final Fast2SmsService sms = new Fast2SmsService();
    private final MailService mail = new MailService();
    private final TokenService tokens = new TokenService();

    public Map<String, Object> sendMobileOtp(String rawMobile) throws SQLException {
        String mobile = Validators.normalizeMobile(rawMobile);
        Optional<User> existing = userDAO.findByMobile(mobile);
        if (existing.isEmpty() && userDAO.isMobileOfDeletedUser(mobile)) {
            throw ApiException.forbidden(ACCOUNT_CLOSED);
        }
        User user = existing.isPresent() ? existing.get() : userDAO.createMinimalByMobile(mobile);
        String otp = OtpUtil.generateSixDigitOtp();
        userDAO.saveOtp(user.getId(), OtpUtil.hashOtp(otp), LocalDateTime.now().plusMinutes(OTP_MINUTES));

        Fast2SmsService.SmsResult smsResult = sms.sendOtpSms(mobile, otp);
        boolean devMode = AppConfig.isDevExposeOtp();
        if (!smsResult.sent() && !devMode) {
            LOG.log(Level.WARNING, "OTP SMS failed: {0}", smsResult.providerResponse());
            throw ApiException.unavailable("OTP could not be sent. Please try again in a moment.");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("mobile_number", mobile);
        data.put("expires_in_minutes", OTP_MINUTES);
        data.put("sms_sent", smsResult.sent());
        if (devMode) {
            data.put("dev_otp", otp);
            if (!smsResult.sent()) {
                data.put("sms_provider_response", smsResult.providerResponse());
            }
        }
        return data;
    }

    public Map<String, Object> verifyMobileOtp(String rawMobile, String otp) throws SQLException {
        String mobile = Validators.normalizeMobile(rawMobile);
        if (ServletUtil.isBlank(otp)) {
            throw new IllegalArgumentException("otp zaroori hai");
        }
        User user = userDAO.findByMobile(mobile)
                .orElseThrow(() -> new IllegalArgumentException("Pehle OTP send karo"));
        assertOtpValid(user, otp);
        alertIfNewUser(user, "mobile " + mobile);
        userDAO.markMobileVerified(user.getId());
        return buildAuthPayload(user);
    }

    public Map<String, Object> sendEmailOtp(String rawEmail) throws SQLException {
        String email = Validators.normalizeEmail(rawEmail);
        Optional<User> existing = userDAO.findByEmail(email);
        if (existing.isEmpty() && userDAO.isEmailOfDeletedUser(email)) {
            throw ApiException.forbidden(ACCOUNT_CLOSED);
        }
        User user = existing.isPresent() ? existing.get() : userDAO.createMinimalByEmail(email);
        String otp = OtpUtil.generateSixDigitOtp();
        userDAO.saveOtp(user.getId(), OtpUtil.hashOtp(otp), LocalDateTime.now().plusMinutes(OTP_MINUTES));

        boolean sent = mail.sendOtpEmail(email, otp);
        boolean devMode = AppConfig.isDevExposeOtp();
        if (!sent && !devMode) {
            throw ApiException.unavailable("OTP could not be sent. Please try again in a moment.");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("expires_in_minutes", OTP_MINUTES);
        if (devMode) {
            data.put("dev_otp", otp);
        }
        return data;
    }

    public Map<String, Object> verifyEmailOtp(String rawEmail, String otp) throws SQLException {
        String email = Validators.normalizeEmail(rawEmail);
        if (ServletUtil.isBlank(otp)) {
            throw new IllegalArgumentException("otp zaroori hai");
        }
        User user = userDAO.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Pehle email OTP send karo"));
        assertOtpValid(user, otp);
        alertIfNewUser(user, email);
        userDAO.markEmailVerified(user.getId());
        return buildAuthPayload(user);
    }

    public Map<String, Object> loginWithEmailPassword(String email, String password) throws SQLException {
        if (ServletUtil.isBlank(email)) {
            throw new IllegalArgumentException("email zaroori hai");
        }
        if (ServletUtil.isBlank(password)) {
            throw new IllegalArgumentException("password zaroori hai");
        }
        User user = userDAO.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Galat email ya password"));
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw ApiException.tooManyRequests("Too many failed attempts. Please try again after " + LOCK_MINUTES + " minutes.");
        }
        if (ServletUtil.isBlank(user.getPasswordHash()) || !PasswordUtil.matches(password, user.getPasswordHash())) {
            userDAO.recordLoginFailure(user.getId(), MAX_LOGIN_FAILURES, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            throw new IllegalArgumentException("Galat email ya password");
        }
        userDAO.clearLoginFailures(user.getId());
        return buildAuthPayload(user);
    }

    public Map<String, Object> sendForgotPasswordOtp(String rawEmail) throws SQLException {
        String normalized = Validators.normalizeEmail(rawEmail);
        Optional<User> opt = userDAO.findByEmail(normalized);
        Map<String, Object> data = new HashMap<>();
        data.put("email", normalized);
        data.put("expires_in_minutes", OTP_MINUTES);
        data.put("message", "Agar yeh email registered hai to OTP bhej diya gaya.");
        if (opt.isEmpty()) {
            return data;
        }
        String otp = OtpUtil.generateSixDigitOtp();
        userDAO.saveOtp(opt.get().getId(), OtpUtil.hashOtp(otp), LocalDateTime.now().plusMinutes(OTP_MINUTES));
        mail.sendOtpEmail(normalized, otp);
        if (AppConfig.isDevExposeOtp()) {
            data.put("dev_otp", otp);
        }
        return data;
    }

    public Map<String, Object> resetPasswordWithEmailOtp(String rawEmail, String otp, String newPassword)
            throws SQLException {
        String email = Validators.normalizeEmail(rawEmail);
        if (ServletUtil.isBlank(otp)) {
            throw new IllegalArgumentException("otp zaroori hai");
        }
        validateNewPassword(newPassword);
        // Same message for an unknown email, so this API cannot be used to find registered emails.
        User user = userDAO.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Galat OTP"));
        assertOtpValid(user, otp);
        userDAO.updatePasswordHash(user.getId(), PasswordUtil.hash(newPassword));
        tokens.revokeAll(user.getId());
        userDAO.markEmailVerified(user.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("password_updated", true);
        return data;
    }

    /** Sets the first password, or changes it. Ends all other logins and returns new tokens for this one. */
    public Map<String, Object> changePassword(User user, String currentPassword, String newPassword) throws SQLException {
        if (!ServletUtil.isBlank(user.getPasswordHash())
                && (ServletUtil.isBlank(currentPassword) || !PasswordUtil.matches(currentPassword, user.getPasswordHash()))) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validateNewPassword(newPassword);
        userDAO.updatePasswordHash(user.getId(), PasswordUtil.hash(newPassword));
        tokens.revokeAll(user.getId());
        return buildAuthPayload(user);
    }

    public User requireUserByToken(String token) throws SQLException {
        if (ServletUtil.isBlank(token)) {
            throw ApiException.unauthorized("access_token missing");
        }
        JwtService.Claims claims = JwtService.shared().verify(token, JwtService.SCOPE_USER);
        User user = userDAO.findById(claims.subjectId())
                .filter(u -> (u.getTokenVersion() == null ? 0 : u.getTokenVersion()) == claims.tokenVersion())
                .orElseThrow(() -> new ApiException(401, "INVALID_TOKEN", "Invalid access token"));
        ensureUserAccountActive(user);
        return user;
    }

    private Map<String, Object> buildAuthPayload(User user) throws SQLException {
        User current = userDAO.findById(user.getId()).orElse(user);
        ensureUserAccountActive(current);
        userDAO.markLogin(user.getId());
        User fresh = userDAO.findById(user.getId()).orElse(user);

        int step = fresh.getOnboardingStep() == null ? 0 : fresh.getOnboardingStep();
        boolean profileComplete = step >= 3 && OnboardingService.isProfileComplete(fresh);

        Map<String, Object> data = new HashMap<>(tokens.issueFor(fresh));
        data.put("onboarding_step", step);
        data.put("profile_complete", profileComplete);
        data.put("needs_registration", step < 3 || !profileComplete);
        data.put("next_action", profileComplete ? "home" : "onboarding_step_" + Math.min(step + 1, 3));
        return data;
    }

    /** Counts a customer as new at their first verified sign-in, so mistyped numbers raise no alert. */
    private void alertIfNewUser(User user, String contact) {
        if (user.getMobileVerifiedAt() == null && user.getEmailVerifiedAt() == null) {
            new AdminAlertService().raise("new_user", "New customer",
                    "New customer signed up with " + contact, "admin-user-view.html?id=" + user.getId());
        }
    }

    /** A wrong OTP counts against the user; the DAO drops the OTP after too many tries. */
    private void assertOtpValid(User user, String otp) throws SQLException {
        if (user.getOtpExpiresAt() == null || user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP expire ho gaya — dubara send karo");
        }
        if (ServletUtil.isBlank(user.getOtpHash()) || !OtpUtil.verifyOtp(otp, user.getOtpHash())) {
            userDAO.recordOtpFailure(user.getId());
            throw new IllegalArgumentException("Galat OTP");
        }
    }

    private static void validateNewPassword(String password) {
        if (ServletUtil.isBlank(password)) {
            throw new IllegalArgumentException("naya password zaroori hai");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("password kam se kam 8 characters ka hona chahiye");
        }
    }

    private static void ensureUserAccountActive(User user) {
        String status = user.getAccountStatus();
        if (status != null && !"active".equalsIgnoreCase(status.trim())) {
            throw ApiException.forbidden("Account is not active. Contact Narainsons support.");
        }
    }
}

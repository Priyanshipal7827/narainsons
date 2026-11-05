package com.narainsons.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.narainsons.util.AdminProfilePhotoStore;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.AdminDAO;
import com.narainsons.model.Admin;
import com.narainsons.util.ApiException;
import com.narainsons.util.JwtService;
import com.narainsons.util.Validators;
import com.narainsons.util.OtpUtil;
import com.narainsons.util.PasswordUtil;
import com.narainsons.util.ServletUtil;

public class AdminAuthService {

    private static final int OTP_MINUTES = 10;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int LOCK_MINUTES = 15;

    private final AdminDAO adminDAO = new AdminDAO();
    private final MailService mail = new MailService();

    public Map<String, Object> login(String email, String password) throws SQLException {
        if (ServletUtil.isBlank(email) || ServletUtil.isBlank(password)) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        Admin admin = adminDAO.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw ApiException.tooManyRequests("Too many failed attempts. Please try again after " + LOCK_MINUTES + " minutes.");
        }
        if (!PasswordUtil.matches(password, admin.getPasswordHash())) {
            adminDAO.recordLoginFailure(admin.getId(), MAX_LOGIN_FAILURES, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!"active".equalsIgnoreCase(admin.getAccountStatus())) {
            throw ApiException.forbidden("Admin account is not active. Contact a super admin.");
        }
        adminDAO.clearLoginFailures(admin.getId());
        return buildSession(admin);
    }

    public Map<String, Object> register(String email, String password, String fullName, String mobile, Admin creator)
            throws SQLException {
        if (creator != null && !"super_admin".equals(creator.getRole())) {
            throw new IllegalArgumentException("Only a super_admin can add a new admin");
        }
        if (creator == null && adminDAO.countAll() > 0) {
            throw new IllegalArgumentException("An admin already exists. Sign in to register additional admins.");
        }
        validateNewPassword(password);
        if (ServletUtil.isBlank(fullName)) {
            throw new IllegalArgumentException("full_name is required");
        }
        String normalizedEmail = Validators.normalizeEmail(email);
        if (adminDAO.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("This email is already registered");
        }
        Admin a = new Admin();
        a.setEmail(normalizedEmail);
        a.setPasswordHash(PasswordUtil.hash(password));
        a.setFullName(fullName.trim());
        a.setMobileNumber(mobile);
        a.setRole(creator == null ? "super_admin" : "admin");
        a.setAccountStatus("active");
        long id = adminDAO.create(a);
        Admin saved = adminDAO.findById(id).orElseThrow();
        Map<String, Object> data = new HashMap<>();
        data.put("admin", sanitize(saved));
        data.put("message", "Admin account created");
        return data;
    }

    public Map<String, Object> sendForgotPasswordOtp(String email) throws SQLException {
        String normalized = Validators.normalizeEmail(email);
        Map<String, Object> data = new HashMap<>();
        data.put("email", normalized);
        data.put("message", "If this admin email is registered, an OTP has been sent.");
        var opt = adminDAO.findByEmail(normalized);
        if (opt.isEmpty() || !"active".equals(opt.get().getAccountStatus())) {
            return data;
        }
        String otp = OtpUtil.generateSixDigitOtp();
        Admin admin = opt.get();
        adminDAO.saveOtp(admin.getId(), OtpUtil.hashOtp(otp), LocalDateTime.now().plusMinutes(OTP_MINUTES));
        mail.sendOtpEmail(normalized, otp);
        // Never fall back to returning the OTP when mail fails: this API needs no login.
        if (AppConfig.isDevExposeOtp()) {
            data.put("dev_otp", otp);
        }
        return data;
    }

    public Map<String, Object> resetPassword(String email, String otp, String newPassword) throws SQLException {
        String normalized = Validators.normalizeEmail(email);
        validateNewPassword(newPassword);
        // Same message for an unknown email, so this API cannot be used to find admin emails.
        Admin admin = adminDAO.findByEmail(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTP"));
        assertOtp(admin, otp);
        adminDAO.updatePasswordHash(admin.getId(), PasswordUtil.hash(newPassword));
        Map<String, Object> data = new HashMap<>();
        data.put("password_updated", true);
        return data;
    }

    public Admin requireByToken(String token) throws SQLException {
        if (ServletUtil.isBlank(token)) {
            throw ApiException.unauthorized("admin access_token missing");
        }
        JwtService.Claims claims = JwtService.shared().verify(token, JwtService.SCOPE_ADMIN);
        return adminDAO.findById(claims.subjectId())
                .filter(a -> "active".equalsIgnoreCase(a.getAccountStatus()) && a.getTokenVersion() == claims.tokenVersion())
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired admin session"));
    }

    public Map<String, Object> profile(Admin admin) {
        return Map.of("admin", sanitize(admin));
    }

    public Map<String, Object> updateProfile(Admin admin, String fullName, String mobile) throws SQLException {
        if (!ServletUtil.isBlank(fullName)) {
            adminDAO.updateProfile(admin.getId(), fullName.trim(), mobile);
            admin = adminDAO.findById(admin.getId()).orElse(admin);
        }
        return profile(admin);
    }

    public Map<String, Object> uploadProfilePhoto(Admin admin, Part part) throws SQLException, IOException {
        if (part == null || part.getSize() <= 0) {
            throw new IllegalArgumentException("Choose a photo file to upload");
        }
        String oldPath = admin.getProfileImagePath();
        String saved = AdminProfilePhotoStore.save(
                admin.getId(),
                part.getInputStream(),
                part.getContentType(),
                part.getSubmittedFileName());
        adminDAO.updateProfileImagePath(admin.getId(), saved);
        if (!ServletUtil.isBlank(oldPath) && !oldPath.equals(saved)) {
            AdminProfilePhotoStore.deleteIfExists(oldPath);
        }
        Admin fresh = adminDAO.findById(admin.getId()).orElse(admin);
        return profile(fresh);
    }

    public void writeProfilePhoto(Admin admin, HttpServletResponse response) throws IOException {
        Path file = AdminProfilePhotoStore.resolve(admin.getProfileImagePath());
        if (file == null) {
            throw ApiException.notFound("No profile photo uploaded yet");
        }
        String probe = Files.probeContentType(file);
        response.setContentType(probe != null ? probe : "image/jpeg");
        response.setHeader("Cache-Control", "private, max-age=120");
        Files.copy(file, response.getOutputStream());
    }

    private Map<String, Object> buildSession(Admin admin) throws SQLException {
        adminDAO.markLogin(admin.getId());
        Admin fresh = adminDAO.findById(admin.getId()).orElse(admin);
        long seconds = AppConfig.getAdminSessionHours() * 3600L;
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", JwtService.shared().issue(fresh.getId(), JwtService.SCOPE_ADMIN, fresh.getTokenVersion(), seconds));
        data.put("token_type", "Bearer");
        data.put("expires_in", seconds);
        data.put("admin", sanitize(fresh));
        return data;
    }

    public static Map<String, Object> sanitize(Admin a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("email", a.getEmail());
        m.put("full_name", a.getFullName());
        m.put("mobile_number", a.getMobileNumber());
        m.put("has_profile_photo", !ServletUtil.isBlank(a.getProfileImagePath()));
        m.put("role", a.getRole());
        m.put("account_status", a.getAccountStatus());
        m.put("last_login", a.getLastLogin());
        m.put("created_at", a.getCreatedAt());
        m.put("updated_at", a.getUpdatedAt());
        return m;
    }

    public Map<String, Object> changePassword(Admin admin, String currentPassword, String newPassword)
            throws SQLException {
        if (ServletUtil.isBlank(currentPassword) || ServletUtil.isBlank(newPassword)) {
            throw new IllegalArgumentException("Current and new password are required");
        }
        if (!PasswordUtil.matches(currentPassword, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        validateNewPassword(newPassword);
        // This ends every session of the admin, so hand the current browser a new one.
        adminDAO.updatePasswordHash(admin.getId(), PasswordUtil.hash(newPassword));
        Map<String, Object> data = new HashMap<>(buildSession(admin));
        data.put("password_updated", true);
        return data;
    }

    private void assertOtp(Admin admin, String otp) throws SQLException {
        if (admin.getOtpExpiresAt() == null || admin.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP expired. Please request a new one.");
        }
        if (ServletUtil.isBlank(otp) || ServletUtil.isBlank(admin.getOtpHash())
                || !OtpUtil.verifyOtp(otp, admin.getOtpHash())) {
            adminDAO.recordOtpFailure(admin.getId());
            throw new IllegalArgumentException("Invalid OTP");
        }
    }

    private static void validateNewPassword(String password) {
        if (ServletUtil.isBlank(password) || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }
}

package com.narainsons.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.AdminDAO;
import com.narainsons.model.Admin;

import jakarta.servlet.http.HttpServletRequest;

public final class AdminAuth {

    private static final AdminDAO ADMIN_DAO = new AdminDAO();

    private AdminAuth() {
    }

    public static Admin requireAdmin(HttpServletRequest request) throws SQLException {
        String bearer = ServletUtil.extractBearerToken(request);
        if (!ServletUtil.isBlank(bearer)) {
            JwtService.Claims claims = JwtService.shared().verify(bearer, JwtService.SCOPE_ADMIN);
            Admin admin = ADMIN_DAO.findById(claims.subjectId())
                    .filter(a -> "active".equalsIgnoreCase(a.getAccountStatus()) && a.getTokenVersion() == claims.tokenVersion())
                    .orElseThrow(() -> ApiException.unauthorized("Unauthorized — admin sign-in required"));
            return admin;
        }
        String key = request.getHeader("X-Admin-Key");
        if (secretMatches(AppConfig.getAdminApiKey(), key)) {
            Admin legacy = new Admin();
            legacy.setId(0L);
            legacy.setEmail("legacy-api-key@system");
            legacy.setFullName("API Key Admin");
            legacy.setRole("super_admin");
            legacy.setAccountStatus("active");
            return legacy;
        }
        throw ApiException.unauthorized("Unauthorized — admin Bearer token or X-Admin-Key required");
    }

    public static void requireSuperAdmin(Admin admin) {
        if (!"super_admin".equals(admin.getRole())) {
            throw ApiException.forbidden("Only a super admin can do this");
        }
    }

    public static void requireCronSecret(HttpServletRequest request) {
        if (!secretMatches(AppConfig.getCronEmiSecret(), request.getHeader("X-Cron-Secret"))) {
            throw ApiException.unauthorized("Unauthorized — valid X-Cron-Secret required");
        }
    }

    private static boolean secretMatches(String expected, String given) {
        if (ServletUtil.isBlank(expected) || given == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                given.trim().getBytes(StandardCharsets.UTF_8));
    }
}

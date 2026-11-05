package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.model.Admin;
import com.narainsons.service.AdminAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.RateLimiter;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@MultipartConfig(maxFileSize = 3145728, maxRequestSize = 4194304)
public class AdminAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final AdminAuthService authService = new AdminAuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            if (parts.length == 2 && "profile".equals(parts[0]) && "photo".equals(parts[1])) {
                Admin admin = authService.requireByToken(ServletUtil.extractBearerToken(request));
                authService.writeProfilePhoto(admin, response);
                return;
            }
            ServletUtil.prepareJsonResponse(response);
            if (parts.length == 1 && "me".equals(parts[0])) {
                Admin admin = authService.requireByToken(ServletUtil.extractBearerToken(request));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Admin profile", authService.profile(admin)));
                return;
            }
            ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown path"));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            String contentType = request.getContentType();
            if (parts.length == 2 && "profile".equals(parts[0]) && "photo".equals(parts[1])
                    && contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
                Admin admin = authService.requireByToken(ServletUtil.extractBearerToken(request));
                Part photo = request.getPart("photo");
                Map<String, Object> data = authService.uploadProfilePhoto(admin, photo);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Profile photo saved", data));
                return;
            }
            JsonObject json = JsonBody.readObject(request);
            if (parts.length == 1 && "login".equals(parts[0])) {
                RateLimiter.check("admin-login-ip:" + request.getRemoteAddr(), 30, 15);
                Map<String, Object> data = authService.login(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "password", null));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Admin login", data));
                return;
            }
            if (parts.length == 1 && "register".equals(parts[0])) {
                Admin creator = null;
                try {
                    creator = authService.requireByToken(ServletUtil.extractBearerToken(request));
                } catch (Exception ignored) {
                    // bootstrap first admin without token
                }
                Map<String, Object> data = authService.register(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "password", null),
                        JsonBody.string(json, "full_name", "fullName"),
                        JsonBody.string(json, "mobile_number", "mobileNumber"),
                        creator);
                ServletUtil.writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.ok("Admin registered", data));
                return;
            }
            if (parts.length == 3 && "password".equals(parts[0]) && "forgot".equals(parts[1]) && "send".equals(parts[2])) {
                RateLimiter.check("admin-otp-ip:" + request.getRemoteAddr(), 10, 60);
                Map<String, Object> data = authService.sendForgotPasswordOtp(JsonBody.string(json, "email", null));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Success", data));
                return;
            }
            if (parts.length == 3 && "password".equals(parts[0]) && "forgot".equals(parts[1]) && "reset".equals(parts[2])) {
                RateLimiter.check("admin-reset-ip:" + request.getRemoteAddr(), 30, 15);
                Map<String, Object> data = authService.resetPassword(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "otp", null),
                        JsonBody.string(json, "new_password", "newPassword"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Password updated", data));
                return;
            }
            if (parts.length == 1 && "profile".equals(parts[0])) {
                Admin admin = authService.requireByToken(ServletUtil.extractBearerToken(request));
                Map<String, Object> data = authService.updateProfile(
                        admin,
                        JsonBody.string(json, "full_name", "fullName"),
                        JsonBody.string(json, "mobile_number", "mobileNumber"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Profile updated", data));
                return;
            }
            if (parts.length == 2 && "password".equals(parts[0]) && "change".equals(parts[1])) {
                Admin admin = authService.requireByToken(ServletUtil.extractBearerToken(request));
                Map<String, Object> data = authService.changePassword(
                        admin,
                        JsonBody.string(json, "current_password", "currentPassword"),
                        JsonBody.string(json, "new_password", "newPassword"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Password updated", data));
                return;
            }
            ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown action"));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }
}

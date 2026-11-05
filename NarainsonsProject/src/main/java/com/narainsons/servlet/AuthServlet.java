package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.dao.DeviceDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.service.OtpAuthService;
import com.narainsons.service.TokenService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.RateLimiter;
import com.narainsons.util.ServletUtil;
import com.narainsons.util.Validators;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        String action = resolveAction(request.getPathInfo());
        try {
            JsonObject json = JsonBody.readObject(request);
            applyRateLimit(action, json, request.getRemoteAddr());
            Map<String, Object> data = switch (action) {
                case "otp/send" -> authService.sendMobileOtp(JsonBody.string(json, "mobile_number", "mobileNumber"));
                case "otp/verify" -> authService.verifyMobileOtp(
                        JsonBody.string(json, "mobile_number", "mobileNumber"),
                        JsonBody.string(json, "otp", null));
                case "email/send" -> authService.sendEmailOtp(JsonBody.string(json, "email", null));
                case "email/verify" -> authService.verifyEmailOtp(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "otp", null));
                case "token/refresh" -> new TokenService().refresh(JsonBody.string(json, "refresh_token", "refreshToken"));
                case "logout" -> logout(authService, request, json);
                case "login" -> authService.loginWithEmailPassword(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "password", null));
                case "password/forgot/send" -> authService.sendForgotPasswordOtp(JsonBody.string(json, "email", null));
                case "password/forgot/reset" -> authService.resetPasswordWithEmailOtp(
                        JsonBody.string(json, "email", null),
                        JsonBody.string(json, "otp", null),
                        JsonBody.string(json, "new_password", "newPassword"));
                default -> throw new IllegalArgumentException("Unknown auth action: " + action);
            };
            ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Success", data));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    /** Removes this phone's push token and ends this login; all_devices ends every login. */
    private static Map<String, Object> logout(OtpAuthService authService, HttpServletRequest request, JsonObject json)
            throws SQLException {
        User user = authService.requireUserByToken(ServletUtil.extractBearerToken(request));
        TokenService tokens = new TokenService();
        String fcmToken = JsonBody.string(json, "fcm_token", "fcmToken");
        if (!ServletUtil.isBlank(fcmToken)) {
            new DeviceDAO().removeTokenForUser(user.getId(), fcmToken.trim());
        }
        tokens.revoke(JsonBody.string(json, "refresh_token", "refreshToken"));
        UserDAO users = new UserDAO();
        if (json.has("all_devices") && json.get("all_devices").getAsBoolean()) {
            tokens.revokeAll(user.getId());
            new DeviceDAO().removeAllForUser(user.getId());
            users.bumpTokenVersion(user.getId());
        }
        return Map.of("logged_out", true);
    }

    /** OTP sends cost money and every guess needs a limit, per caller and per target account. */
    private static void applyRateLimit(String action, JsonObject json, String ip) {
        String target = JsonBody.string(json, "email", null);
        if (target == null) {
            target = JsonBody.string(json, "mobile_number", "mobileNumber");
        }
        target = rateLimitKey(target);
        if (action.endsWith("/send")) {
            RateLimiter.check("otp-send-ip:" + ip, 20, 60);
            RateLimiter.check("otp-send:" + target, 5, 15);
        } else {
            RateLimiter.check("auth-ip:" + ip, 60, 15);
        }
    }

    /** "+91 98765 43210" and "9876543210" are one number, so they must share one limit. */
    private static String rateLimitKey(String target) {
        if (target == null) {
            return "";
        }
        if (target.contains("@")) {
            return target.trim().toLowerCase();
        }
        try {
            return Validators.normalizeMobile(target);
        } catch (IllegalArgumentException e) {
            return target.trim();
        }
    }

    private static String resolveAction(String pathInfo) {
        if (pathInfo == null || pathInfo.equals("/")) {
            return "";
        }
        String action = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (action.endsWith("/")) {
            action = action.substring(0, action.length() - 1);
        }
        return action;
    }
}

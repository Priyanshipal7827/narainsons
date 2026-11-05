package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.dao.DeviceDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.service.TokenService;
import com.narainsons.util.ApiException;
import com.narainsons.util.Validators;
import com.narainsons.service.OnboardingService;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UserServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        try {
            User user = authService.requireUserByToken(extractToken(request));
            Map<String, Object> data = profilePayload(user);
            ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Profile", data));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        OnboardingService onboardingService = new OnboardingService();
        ServletUtil.prepareJsonResponse(response);
        String action = resolveAction(request.getPathInfo());
        try {
            User user = authService.requireUserByToken(extractToken(request));
            JsonObject json = JsonBody.readObject(request);
            if ("onboarding".equals(action)) {
                String stepText = JsonBody.string(json, "step", null);
                if (ServletUtil.isBlank(stepText)) {
                    throw new IllegalArgumentException("step zaroori hai (1, 2 ya 3)");
                }
                int step = Integer.parseInt(stepText.trim());
                User updated = onboardingService.saveStep(user, step, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Step " + step + " saved", profilePayload(updated)));
                return;
            }
            if ("sync".equals(action)) {
                User updated = onboardingService.saveDeviceSync(user, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Contacts & gallery sync", profilePayload(updated)));
                return;
            }
            if ("password".equals(action)) {
                Map<String, Object> data = authService.changePassword(user,
                        JsonBody.string(json, "current_password", "currentPassword"),
                        JsonBody.string(json, "new_password", "newPassword"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Password updated", data));
                return;
            }
            if ("devices".equals(action)) {
                String token = JsonBody.string(json, "fcm_token", "fcmToken");
                if (ServletUtil.isBlank(token)) {
                    throw new IllegalArgumentException("fcm_token is required");
                }
                String platformRaw = JsonBody.string(json, "platform", null);
                String platform = Validators.oneOf("platform", platformRaw == null ? "android" : platformRaw, "android", "ios", "web");
                new DeviceDAO().upsert(user.getId(), token.trim(), platform,
                        JsonBody.string(json, "device_id", "deviceId"), JsonBody.string(json, "app_version", "appVersion"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Device registered", Map.of("registered", true)));
                return;
            }
            if ("devices/remove".equals(action)) {
                String token = JsonBody.string(json, "fcm_token", "fcmToken");
                if (ServletUtil.isBlank(token)) {
                    throw new IllegalArgumentException("fcm_token is required");
                }
                new DeviceDAO().removeTokenForUser(user.getId(), token.trim());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Device removed", Map.of("removed", true)));
                return;
            }
            if ("notification-settings".equals(action)) {
                if (!json.has("enabled") || !json.get("enabled").isJsonPrimitive()) {
                    throw new IllegalArgumentException("enabled (true/false) is required");
                }
                boolean enabled = json.get("enabled").getAsBoolean();
                new UserDAO().updateNotificationEnabled(user.getId(), enabled);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Notification settings saved", Map.of("notification_enable", enabled)));
                return;
            }
            if ("delete".equals(action)) {
                if (!json.has("confirm") || !json.get("confirm").getAsBoolean()) {
                    throw new IllegalArgumentException("Send confirm: true to delete the account");
                }
                if (new LoanApplicationDAO().hasOpenLoanForUser(user.getId())) {
                    throw ApiException.conflict("OPEN_LOAN", "Account cannot be deleted while a loan is open");
                }
                new UserDAO().softDelete(user.getId());
                new TokenService().revokeAll(user.getId());
                new DeviceDAO().removeAllForUser(user.getId());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Account deleted", Map.of("deleted", true)));
                return;
            }
            ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown action"));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    private static Map<String, Object> profilePayload(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("onboarding_step", user.getOnboardingStep());
        data.put("profile_complete", OnboardingService.isProfileComplete(user));
        data.put("needs_registration", !OnboardingService.isProfileComplete(user));
        data.put("user", ServletUtil.userView(user));
        return data;
    }

    private static String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return request.getHeader("X-Access-Token");
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

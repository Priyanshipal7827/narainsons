package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.model.User;
import com.narainsons.service.NotificationService;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class NotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService auth = new OtpAuthService();
        NotificationService service = new NotificationService();
        ServletUtil.prepareJsonResponse(response);
        try {
            User user = auth.requireUserByToken(ServletUtil.extractBearerToken(request));
            int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 50, 1, 100);
            int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, 100_000);
            Map<String, Object> data = service.listForUser(user.getId(), limit, offset);
            ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Notifications", data));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService auth = new OtpAuthService();
        NotificationService service = new NotificationService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = auth.requireUserByToken(ServletUtil.extractBearerToken(request));
            if (parts.length == 1 && "read-all".equals(parts[0])) {
                service.markAllRead(user.getId());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("All marked read", null));
                return;
            }
            if (parts.length >= 2 && "read".equals(parts[0])) {
                long id = Long.parseLong(parts[1]);
                service.markRead(user.getId(), id);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Marked read", null));
                return;
            }
            if (parts.length == 1 && "read".equals(parts[0])) {
                JsonObject json = JsonBody.readObject(request);
                String idText = JsonBody.string(json, "notification_id", "notificationId");
                if (idText != null) {
                    service.markRead(user.getId(), Long.parseLong(idText));
                }
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Marked read", null));
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

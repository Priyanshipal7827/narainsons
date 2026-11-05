package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.narainsons.service.EmiReminderService;
import com.narainsons.util.AdminAuth;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Windows Task Scheduler / cron: har hour POST call karo.
 * Header: X-Cron-Secret = app.properties cron.emi.secret
 */
public class InternalEmiReminderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ServletUtil.prepareJsonResponse(response);
        try {
            AdminAuth.requireCronSecret(request);
            Map<String, Object> data = new EmiReminderService().runHourlyReminders();
            ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI reminders processed", data));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }
}

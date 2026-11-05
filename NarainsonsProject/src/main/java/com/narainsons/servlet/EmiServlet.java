package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.model.User;
import com.narainsons.service.EmiService;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EmiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final EmiService emiService = new EmiService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService auth = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = auth.requireUserByToken(ServletUtil.extractBearerToken(request));
            String action = parts.length == 0 ? "summary" : parts[0];
            switch (action) {
                case "summary" -> ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("EMI summary", emiService.summary(user)));
                case "schedule" -> ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("EMI schedule", emiService.schedule(user, optionalLong(request.getParameter("loan_id")))));
                case "payment-instructions" -> ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Payment instructions", emiService.paymentInstructions()));
                case "payments" -> {
                    if (parts.length == 2) {
                        Map<String, Object> one = Map.of("payment", emiService.getPayment(user, Long.parseLong(parts[1])));
                        ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI payment", one));
                        return;
                    }
                    int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 20, 1, 50);
                    int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, 100_000);
                    Map<String, Object> data = Map.of("payments", emiService.listPayments(user, limit, offset));
                    ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI payments", data));
                }
                default -> ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown path"));
            }
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    private static Long optionalLong(String raw) {
        return ServletUtil.isBlank(raw) ? null : Long.valueOf(raw.trim());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService auth = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = auth.requireUserByToken(ServletUtil.extractBearerToken(request));
            if (parts.length == 2 && "payments".equals(parts[0]) && "submit".equals(parts[1])) {
                JsonObject json = JsonBody.readObject(request);
                Map<String, Object> data = emiService.submitPayment(user, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.ok("Payment submitted", data));
                return;
            }
            ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown path"));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }
}

package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.model.User;
import com.narainsons.service.LoanApplicationService;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoanServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final LoanApplicationService loanService = new LoanApplicationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = authService.requireUserByToken(ServletUtil.extractBearerToken(request));
            if (parts.length == 0 || "applications".equals(parts[0]) && parts.length == 1) {
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 20, 1, 50);
                int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, 100_000);
                Map<String, Object> data = Map.of("applications", loanService.listForUser(user, limit, offset));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan applications", data));
                return;
            }
            if ("applications".equals(parts[0]) && parts.length == 2) {
                long id = Long.parseLong(parts[1]);
                Map<String, Object> data = Map.of("application", loanService.getOne(user, id));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan application", data));
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = authService.requireUserByToken(ServletUtil.extractBearerToken(request));
            if (parts.length == 1 && "apply".equals(parts[0])) {
                JsonObject json = JsonBody.readObject(request);
                Map<String, Object> data = loanService.apply(user, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.ok("Loan submitted", data));
                return;
            }
            if (parts.length == 1 && "quote".equals(parts[0])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Loan quote", loanService.quote(JsonBody.readObject(request))));
                return;
            }
            if (parts.length == 3 && "applications".equals(parts[0]) && "cancel".equals(parts[2])) {
                Map<String, Object> data = Map.of("application", loanService.cancel(user, Long.parseLong(parts[1])));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Application cancelled", data));
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

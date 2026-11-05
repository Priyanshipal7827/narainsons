package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import com.narainsons.model.User;
import com.narainsons.service.DashboardService;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DashboardServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpAuthService authService = new OtpAuthService();
        DashboardService dashboardService = new DashboardService();
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            User user = authService.requireUserByToken(ServletUtil.extractBearerToken(request));
            if (parts.length == 0 || "home".equals(parts[0])) {
                Map<String, Object> data = dashboardService.buildHome(user);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Dashboard", data));
                return;
            }
            if ("banners".equals(parts[0])) {
                Map<String, Object> data = Map.of("banners", dashboardService.listBanners());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Banners", data));
                return;
            }
            if ("loan-config".equals(parts[0])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Loan config", dashboardService.loanConfigPayload()));
                return;
            }
            if ("loan-apply-video".equals(parts[0])) {
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("how_to_apply_loan_video", dashboardService.loadHowToApplyVideo());
                data.put("videos", dashboardService.listHowToApplyVideos());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan apply guide video", data));
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

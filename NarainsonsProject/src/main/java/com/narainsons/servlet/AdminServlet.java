package com.narainsons.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.dao.AdminDAO;
import com.narainsons.dao.EmiCollectionSettingsDAO;
import com.narainsons.dao.LoanApplyGuideVideoDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.service.AdminAlertService;
import com.narainsons.service.AdminListService;
import com.narainsons.service.AdminOverviewService;
import com.narainsons.util.ApiException;
import com.narainsons.util.TableExport;
import com.narainsons.service.AdminBannerService;
import com.narainsons.service.NotificationService;
import com.narainsons.util.Validators;
import com.narainsons.service.AdminDashboardService;
import com.narainsons.service.AdminDatabaseService;
import com.narainsons.service.AdminLoanApplicationService;
import com.narainsons.service.AdminLoanEmiService;
import com.narainsons.service.AdminUserService;
import com.narainsons.util.AdminAuth;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

@MultipartConfig(maxFileSize = 5242880, maxRequestSize = 6291456)
public class AdminServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final AdminLoanEmiService adminService = new AdminLoanEmiService();
    private final AdminLoanApplicationService loanApplicationService = new AdminLoanApplicationService();
    private final AdminDashboardService dashboardService = new AdminDashboardService();
    private final AdminUserService userService = new AdminUserService();
    private final AdminDatabaseService databaseService = new AdminDatabaseService();
    private final AdminBannerService bannerService = new AdminBannerService();
    private final AdminListService listService = new AdminListService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ServletUtil.prepareJsonResponse(response);
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            var admin = AdminAuth.requireAdmin(request);
            if (parts.length == 2 && "list".equals(parts[0])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("List", listService.list(parts[1], admin, request)));
                return;
            }
            if (parts.length == 3 && "list".equals(parts[0]) && "export".equals(parts[2])) {
                exportList(parts[1], admin, request, response);
                return;
            }
            if (parts.length == 2 && "dashboard".equals(parts[0]) && "summary".equals(parts[1])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Dashboard",
                        new AdminOverviewService().dashboard(request.getParameter("date_from"), request.getParameter("date_to"))));
                return;
            }
            if (parts.length == 1 && "alerts".equals(parts[0])) {
                long after = ServletUtil.parseIntParam(request.getParameter("after_id"), 0, 0, Integer.MAX_VALUE);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Alerts", new AdminAlertService().poll(admin.getId(), after)));
                return;
            }
            if (parts.length == 3 && "loan".equals(parts[0]) && "applications".equals(parts[1])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Loan", new AdminOverviewService().loanDetail(Long.parseLong(parts[2]))));
                return;
            }
            if (parts.length == 1 && "dashboard".equals(parts[0])) {
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Admin dashboard", dashboardService.dashboard(admin)));
                return;
            }
            if (parts.length == 2 && "loan".equals(parts[0]) && "applications".equals(parts[1])) {
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 100, 1, 500);
                int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, 10_000);
                String status = request.getParameter("status");
                String search = request.getParameter("search");
                Map<String, Object> data = loanApplicationService.listApplications(status, search, limit, offset);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan applications", data));
                return;
            }
            if (parts.length == 2 && "emi".equals(parts[0]) && "payments".equals(parts[1])) {
                int emiLimit = ServletUtil.parseIntParam(request.getParameter("limit"), 200, 1, 500);
                Map<String, Object> bundle = databaseService.browseTable("emi_payment_submissions", emiLimit);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) bundle.get("rows");
                if (rows == null) {
                    rows = List.of();
                }
                Map<String, Object> data = new HashMap<>();
                data.put("payments", rows);
                data.put("stats", emiPaymentStats(rows));
                data.put("source", Map.of("database", "narainson_db", "table", "emi_payment_submissions"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI payments", data));
                return;
            }
            if (parts.length == 2 && "settings".equals(parts[0]) && "emi-collection".equals(parts[1])) {
                Map<String, Object> data = new HashMap<>();
                data.put("settings", new EmiCollectionSettingsDAO().findActive().orElse(null));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI collection settings", data));
                return;
            }
            if (parts.length == 1 && "users".equals(parts[0])) {
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 100, 1, 1000);
                int offset = ServletUtil.parseIntParam(request.getParameter("offset"), 0, 0, 10_000);
                String search = request.getParameter("search");
                String accountStatus = request.getParameter("account_status");
                String kycStatus = request.getParameter("kyc_status");
                Map<String, Object> data = userService.listUsers(search, accountStatus, kycStatus, limit, offset);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("App users", data));
                return;
            }
            if (parts.length == 2 && "users".equals(parts[0])) {
                long userId = Long.parseLong(parts[1]);
                Map<String, Object> data = userService.getUser(userId);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("User details", data));
                return;
            }
            if (parts.length == 1 && "banners".equals(parts[0])) {
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 100, 1, 500);
                Map<String, Object> data = bannerService.listDashboardBanners(limit);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Dashboard banners", data));
                return;
            }
            if (parts.length == 2 && "banners".equals(parts[0])) {
                long bannerId = Long.parseLong(parts[1]);
                Map<String, Object> data = bannerService.getBanner(bannerId);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Banner", data));
                return;
            }
            if (parts.length == 1 && "database".equals(parts[0])) {
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 200, 1, 1000);
                Map<String, Object> data = databaseService.browseAll(limit, "super_admin".equals(admin.getRole()));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Database tables", data));
                return;
            }
            if (parts.length == 2 && "database".equals(parts[0])) {
                if ("admins".equals(parts[1])) {
                    AdminAuth.requireSuperAdmin(admin);
                }
                int limit = ServletUtil.parseIntParam(request.getParameter("limit"), 200, 1, 1000);
                Map<String, Object> data = databaseService.browseTable(parts[1], limit);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Table rows", data));
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
            var admin = AdminAuth.requireAdmin(request);
            String contentType = request.getContentType();
            if (parts.length == 2 && "banners".equals(parts[0]) && "upload-image".equals(parts[1])
                    && contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
                Part image = request.getPart("image");
                Map<String, Object> data = bannerService.uploadBannerImage(image, request);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK,
                        ApiResponse.ok("Banner image uploaded", data));
                return;
            }
            JsonObject json = JsonBody.readObject(request);
            String note = JsonBody.string(json, "admin_note", "adminNote");

            if (parts.length == 4 && "loan".equals(parts[0]) && "applications".equals(parts[1])
                    && "approve".equals(parts[3])) {
                long loanId = Long.parseLong(parts[2]);
                Map<String, Object> data = adminService.approveLoan(loanId, note);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan approved", data));
                return;
            }
            if (parts.length == 4 && "loan".equals(parts[0]) && "applications".equals(parts[1])
                    && "reject".equals(parts[3])) {
                long loanId = Long.parseLong(parts[2]);
                Map<String, Object> data = adminService.rejectLoan(loanId, note);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan rejected", data));
                return;
            }
            if (parts.length == 4 && "loan".equals(parts[0]) && "applications".equals(parts[1])
                    && "disburse".equals(parts[3])) {
                Map<String, Object> data = adminService.disburseLoan(Long.parseLong(parts[2]),
                        JsonBody.string(json, "disbursement_reference", "disbursementReference"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Loan disbursed", data));
                return;
            }
            if (parts.length == 3 && "users".equals(parts[0]) && "kyc".equals(parts[2])) {
                Map<String, Object> data = userService.reviewKyc(Long.parseLong(parts[1]),
                        JsonBody.string(json, "action", null), JsonBody.string(json, "reason", null));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("KYC updated", data));
                return;
            }
            if (parts.length == 2 && "settings".equals(parts[0]) && "emi-collection".equals(parts[1])) {
                AdminAuth.requireSuperAdmin(admin);
                String payee = JsonBody.string(json, "payee_name", "payeeName");
                String qr = JsonBody.string(json, "qr_image_url", "qrImageUrl");
                if (ServletUtil.isBlank(payee)) {
                    throw new IllegalArgumentException("payee_name is required");
                }
                if (!ServletUtil.isBlank(qr) && !Validators.isSafeUrl(qr)) {
                    throw new IllegalArgumentException("qr_image_url must be an http or https URL");
                }
                new EmiCollectionSettingsDAO().save(payee, JsonBody.string(json, "upi_id", "upiId"), qr,
                        JsonBody.string(json, "bank_name", "bankName"),
                        JsonBody.string(json, "bank_account_name", "bankAccountName"),
                        JsonBody.string(json, "bank_account_number", "bankAccountNumber"),
                        JsonBody.string(json, "ifsc_code", "ifscCode"),
                        JsonBody.string(json, "payment_note", "paymentNote"));
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Settings saved", null));
                return;
            }
            if (parts.length == 2 && "notifications".equals(parts[0]) && "send".equals(parts[1])) {
                String title = JsonBody.string(json, "title", null);
                String body = JsonBody.string(json, "body", null);
                String userId = JsonBody.string(json, "user_id", "userId");
                boolean toAll = json.has("all") && json.get("all").getAsBoolean();
                if (ServletUtil.isBlank(title) || ServletUtil.isBlank(body) || (!toAll && ServletUtil.isBlank(userId))) {
                    throw new IllegalArgumentException("Choose a customer and enter a title and message");
                }
                NotificationService sender = new NotificationService();
                int sent = 0;
                if (toAll) {
                    AdminAuth.requireSuperAdmin(admin);
                    for (long id : new UserDAO().activeUserIds()) {
                        sender.notifyUser(id, "general", title, body, Map.of());
                        sent++;
                    }
                } else {
                    long id = Long.parseLong(userId.trim());
                    if (new UserDAO().findById(id).isEmpty()) {
                        throw ApiException.notFound("No customer has ID " + id);
                    }
                    sender.notifyUser(id, "general", title, body, Map.of());
                    sent = 1;
                }
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Notification sent", Map.of("sent", sent)));
                return;
            }
            if (parts.length == 2 && "alerts".equals(parts[0]) && "read".equals(parts[1])) {
                new AdminAlertService().markAllRead(admin.getId());
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Marked read", null));
                return;
            }
            if (parts.length == 3 && "admins".equals(parts[0]) && "status".equals(parts[2])) {
                AdminAuth.requireSuperAdmin(admin);
                long targetId = Long.parseLong(parts[1]);
                if (targetId == admin.getId()) {
                    throw new IllegalArgumentException("You cannot change your own account status");
                }
                boolean active = json.has("active") && json.get("active").getAsBoolean();
                if (!new AdminDAO().updateStatus(targetId, active ? "active" : "inactive")) {
                    throw ApiException.notFound("Staff account not found");
                }
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Staff account updated", Map.of("active", active)));
                return;
            }
            if (parts.length >= 1 && "videos".equals(parts[0])) {
                saveVideo(parts, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Saved", null));
                return;
            }
            if (parts.length == 4 && "emi".equals(parts[0]) && "payments".equals(parts[1])
                    && "approve".equals(parts[3])) {
                long payId = Long.parseLong(parts[2]);
                Map<String, Object> data = adminService.approveEmiPayment(payId, note);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI payment approved", data));
                return;
            }
            if (parts.length == 4 && "emi".equals(parts[0]) && "payments".equals(parts[1])
                    && "reject".equals(parts[3])) {
                long payId = Long.parseLong(parts[2]);
                Map<String, Object> data = adminService.rejectEmiPayment(payId, note);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("EMI payment rejected", data));
                return;
            }
            if (parts.length == 3 && "users".equals(parts[0]) && "status".equals(parts[2])) {
                AdminAuth.requireSuperAdmin(admin);
                long userId = Long.parseLong(parts[1]);
                boolean active = true;
                if (json.has("active") && !json.get("active").isJsonNull()) {
                    active = json.get("active").getAsBoolean();
                } else if (json.has("account_status") || json.has("accountStatus")) {
                    String st = JsonBody.string(json, "account_status", "accountStatus");
                    active = st != null && "active".equalsIgnoreCase(st.trim());
                }
                Map<String, Object> data = userService.setUserActive(userId, active);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("User status updated", data));
                return;
            }
            if (parts.length == 3 && "users".equals(parts[0]) && "delete".equals(parts[2])) {
                AdminAuth.requireSuperAdmin(admin);
                long userId = Long.parseLong(parts[1]);
                Map<String, Object> data = userService.deleteUser(userId);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("User deleted", data));
                return;
            }
            if (parts.length == 1 && "banners".equals(parts[0])) {
                Map<String, Object> data = bannerService.createBanner(json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_CREATED,
                        ApiResponse.ok("Banner created", data));
                return;
            }
            if (parts.length == 3 && "banners".equals(parts[0]) && "update".equals(parts[2])) {
                long bannerId = Long.parseLong(parts[1]);
                Map<String, Object> data = bannerService.updateBanner(bannerId, json);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Banner updated", data));
                return;
            }
            if (parts.length == 3 && "banners".equals(parts[0]) && "delete".equals(parts[2])) {
                long bannerId = Long.parseLong(parts[1]);
                Map<String, Object> data = bannerService.deleteBanner(bannerId);
                ServletUtil.writeJson(response, HttpServletResponse.SC_OK, ApiResponse.ok("Banner deleted", data));
                return;
            }

            ServletUtil.writeJson(response, HttpServletResponse.SC_NOT_FOUND, ApiResponse.error("Unknown admin action"));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    private void exportList(String dataset, com.narainsons.model.Admin admin, HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {
        listService.checkExport(dataset, admin, request);
        boolean excel = "xlsx".equalsIgnoreCase(request.getParameter("format"));
        response.setContentType(excel
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"narainsons-export." + (excel ? "xlsx" : "csv") + "\"");
        var out = response.getOutputStream();
        listService.export(dataset, admin, request, excel ? TableExport.xlsx(out) : TableExport.csv(out));
    }

    private static void saveVideo(String[] parts, JsonObject json) throws SQLException {
        LoanApplyGuideVideoDAO dao = new LoanApplyGuideVideoDAO();
        if (parts.length == 3 && "delete".equals(parts[2])) {
            if (!dao.delete(Long.parseLong(parts[1]))) {
                throw ApiException.notFound("Video not found");
            }
            return;
        }
        String title = JsonBody.string(json, "title", null);
        String url = JsonBody.string(json, "video_url", "videoUrl");
        String thumb = JsonBody.string(json, "thumbnail_url", "thumbnailUrl");
        if (ServletUtil.isBlank(title) || ServletUtil.isBlank(url)) {
            throw new IllegalArgumentException("Title and video URL are required");
        }
        if (!Validators.isSafeUrl(url) || (!ServletUtil.isBlank(thumb) && !Validators.isSafeUrl(thumb))) {
            throw new IllegalArgumentException("Video and thumbnail must be http or https URLs");
        }
        String type = Validators.oneOf("video_type", JsonBody.string(json, "video_type", "videoType"), "mp4", "youtube", "vimeo", "hls");
        String seconds = JsonBody.string(json, "duration_seconds", "durationSeconds");
        String order = JsonBody.string(json, "sort_order", "sortOrder");
        boolean active = !json.has("is_active") || json.get("is_active").getAsBoolean();
        Long id = parts.length == 3 && "update".equals(parts[2]) ? Long.valueOf(parts[1]) : null;
        if (parts.length != 1 && id == null) {
            throw ApiException.notFound("Unknown video action");
        }
        boolean saved = dao.save(id, title.trim(), JsonBody.string(json, "description", null), url.trim(),
                ServletUtil.isBlank(thumb) ? null : thumb.trim(), type,
                ServletUtil.isBlank(seconds) ? null : Integer.valueOf(seconds.trim()),
                ServletUtil.isBlank(order) ? 0 : Integer.parseInt(order.trim()), active);
        if (!saved) {
            throw ApiException.notFound("Video not found");
        }
    }

    private static Map<String, Object> emiPaymentStats(List<Map<String, Object>> rows) {
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        for (Map<String, Object> row : rows) {
            Object raw = row.get("status");
            String status = raw == null ? "pending" : String.valueOf(raw).toLowerCase();
            if ("approved".equals(status)) {
                approved++;
            } else if ("rejected".equals(status)) {
                rejected++;
            } else {
                pending++;
            }
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", rows.size());
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        return stats;
    }
}

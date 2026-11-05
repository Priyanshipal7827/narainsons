package com.narainsons.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.UploadedFileDAO;
import com.narainsons.model.User;
import com.narainsons.service.OtpAuthService;
import com.narainsons.util.AdminAuth;
import com.narainsons.util.ApiException;
import com.narainsons.util.ApiResponse;
import com.narainsons.util.JwtService;
import com.narainsons.util.RateLimiter;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

/** Payment proofs and KYC documents: uploaded by the user, readable only by that user and admins. */
@MultipartConfig(maxFileSize = 5242880, maxRequestSize = 6291456)
public class FileServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Set<String> PURPOSES = Set.of("payment_proof", "profile_image", "aadhaar", "pan");

    private final UploadedFileDAO fileDAO = new UploadedFileDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        ServletUtil.prepareJsonResponse(response);
        try {
            User user = new OtpAuthService().requireUserByToken(ServletUtil.extractBearerToken(request));
            RateLimiter.check("upload:" + user.getId(), 30, 60);
            String contentType = request.getContentType();
            if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
                throw new IllegalArgumentException("Send multipart/form-data with fields: file, purpose");
            }
            String purpose = request.getParameter("purpose");
            if (purpose == null || !PURPOSES.contains(purpose)) {
                throw new IllegalArgumentException("purpose must be one of: payment_proof, profile_image, aadhaar, pan");
            }
            Part part = request.getPart("file");
            if (part == null || part.getSize() <= 0) {
                throw new IllegalArgumentException("file is required");
            }

            Path root = uploadRoot();
            String id = UUID.randomUUID().toString().replace("-", "");
            Path temp = root.resolve(id + ".tmp");
            try (InputStream in = part.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Path target;
            String detected;
            try {
                detected = detectType(temp);
                boolean pdfAllowed = "aadhaar".equals(purpose) || "pan".equals(purpose);
                if (detected == null || ("application/pdf".equals(detected) && !pdfAllowed)) {
                    throw new IllegalArgumentException(pdfAllowed
                            ? "Only JPG, PNG, WebP or PDF files are allowed"
                            : "Only JPG, PNG or WebP images are allowed");
                }
                target = root.resolve(id);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            fileDAO.insert(new UploadedFileDAO.FileRow(id, user.getId(), purpose, id, detected, Files.size(target)));

            Map<String, Object> data = new HashMap<>();
            data.put("file_id", id);
            data.put("url", request.getContextPath() + "/api/v1/files/" + id);
            data.put("content_type", detected);
            data.put("size_bytes", Files.size(target));
            ServletUtil.writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.ok("File uploaded", data));
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        try {
            if (parts.length != 1) {
                throw ApiException.notFound("File not found");
            }
            UploadedFileDAO.FileRow file = fileDAO.findById(parts[0])
                    .orElseThrow(() -> ApiException.notFound("File not found"));
            requireAccess(request, file);
            Path path = uploadRoot().resolve(file.storedName());
            if (!Files.isRegularFile(path)) {
                throw ApiException.notFound("File not found");
            }
            response.setContentType(file.contentType());
            response.setHeader("Cache-Control", "private, max-age=300");
            response.setContentLengthLong(Files.size(path));
            Files.copy(path, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            ServletUtil.writeError(response, e);
        } catch (SQLException e) {
            ServletUtil.writeDbError(response, e);
        }
    }

    private static void requireAccess(HttpServletRequest request, UploadedFileDAO.FileRow file) throws SQLException {
        String token = ServletUtil.extractBearerToken(request);
        if (ServletUtil.isBlank(token) && request.getHeader("X-Admin-Key") == null) {
            throw ApiException.unauthorized("Sign in to view this file");
        }
        boolean adminToken = !ServletUtil.isBlank(token)
                && JwtService.SCOPE_ADMIN.equals(JwtService.shared().verify(token).scope());
        if (adminToken || ServletUtil.isBlank(token)) {
            AdminAuth.requireAdmin(request);
            return;
        }
        User user = new OtpAuthService().requireUserByToken(token);
        if (user.getId() != file.userId()) {
            // 404, not 403: other people's file ids should not be confirmable.
            throw ApiException.notFound("File not found");
        }
    }

    private static Path uploadRoot() throws IOException {
        Path root = AppConfig.getUserFilesRoot();
        Files.createDirectories(root);
        return root;
    }

    /** Trusts the file's first bytes, not the name or Content-Type the client sent. */
    private static String detectType(Path file) throws IOException {
        byte[] head = new byte[12];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < 4) {
                return null;
            }
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        if (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return "application/pdf";
        }
        return null;
    }
}

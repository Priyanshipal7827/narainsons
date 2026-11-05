package com.narainsons.servlet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.narainsons.util.BannerImageStore;
import com.narainsons.util.ServletUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Public banner images for app + admin preview (uploaded files). */
public class MediaServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = ServletUtil.splitPathInfo(request.getPathInfo());
        if (parts.length != 2 || !"banners".equals(parts[0])) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path file = BannerImageStore.resolve(parts[1]);
        if (file == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String probe = Files.probeContentType(file);
        response.setContentType(probe != null ? probe : "image/jpeg");
        response.setHeader("Cache-Control", "public, max-age=86400");
        Files.copy(file, response.getOutputStream());
    }
}

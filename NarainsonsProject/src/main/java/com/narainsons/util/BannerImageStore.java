package com.narainsons.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

public final class BannerImageStore {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private BannerImageStore() {
    }

    public static Path uploadRoot() {
        String base = System.getProperty("catalina.base");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home", ".");
        }
        Path root = Path.of(base, "narainsons-uploads", "banner-images");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create banner upload directory", e);
        }
        return root;
    }

    public static void validateUpload(String contentType, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Image file is empty");
        }
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("Banner image must be 5 MB or smaller");
        }
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP, or GIF images are allowed");
        }
    }

    public static String save(InputStream input, String contentType, String submittedFileName) throws IOException {
        String ext = AdminProfilePhotoStore.extensionFor(contentType, submittedFileName);
        String fileName = "banner-" + UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = uploadRoot().resolve(fileName);
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        try {
            validateUpload(contentType, Files.size(target));
        } catch (IllegalArgumentException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return fileName;
    }

    public static Path resolve(String storedFileName) {
        if (ServletUtil.isBlank(storedFileName)) {
            return null;
        }
        if (storedFileName.contains("..") || storedFileName.contains("/") || storedFileName.contains("\\")) {
            return null;
        }
        Path p = uploadRoot().resolve(storedFileName).normalize();
        if (!p.startsWith(uploadRoot())) {
            return null;
        }
        return Files.isRegularFile(p) ? p : null;
    }

    public static String publicUrl(HttpServletRequest request, String fileName) {
        String ctx = request.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                + ctx + "/api/media/banners/" + fileName;
    }
}

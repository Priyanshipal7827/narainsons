package com.narainsons.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

public final class AdminProfilePhotoStore {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 3L * 1024 * 1024;

    private AdminProfilePhotoStore() {
    }

    public static Path uploadRoot() {
        String base = System.getProperty("catalina.base");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home", ".");
        }
        Path root = Path.of(base, "narainsons-uploads", "admin-profiles");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory", e);
        }
        return root;
    }

    public static String extensionFor(String contentType, String submittedName) {
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> extFromName(submittedName);
            };
        }
        return extFromName(submittedName);
    }

    private static String extFromName(String name) {
        if (name == null) {
            return ".jpg";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String ext = name.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(jpe?g|png|webp|gif)")) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return ".jpg";
    }

    public static void validateUpload(String contentType, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Photo file is empty");
        }
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("Photo must be 3 MB or smaller");
        }
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP, or GIF images are allowed");
        }
    }

    public static String save(long adminId, InputStream input, String contentType, String submittedFileName)
            throws IOException {
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP, or GIF images are allowed");
        }
        String ext = extensionFor(contentType, submittedFileName);
        String fileName = "admin-" + adminId + ext;
        Path target = uploadRoot().resolve(fileName);
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        long size = Files.size(target);
        if (size <= 0) {
            Files.deleteIfExists(target);
            throw new IllegalArgumentException("Photo file is empty");
        }
        if (size > MAX_BYTES) {
            Files.deleteIfExists(target);
            throw new IllegalArgumentException("Photo must be 3 MB or smaller");
        }
        return fileName;
    }

    public static Path resolve(String storedFileName) {
        if (ServletUtil.isBlank(storedFileName)) {
            return null;
        }
        Path p = uploadRoot().resolve(storedFileName).normalize();
        if (!p.startsWith(uploadRoot())) {
            return null;
        }
        return Files.isRegularFile(p) ? p : null;
    }

    public static void deleteIfExists(String storedFileName) throws IOException {
        Path p = resolve(storedFileName);
        if (p != null) {
            Files.deleteIfExists(p);
        }
    }
}

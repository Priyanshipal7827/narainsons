package com.narainsons.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validators() {
    }

    /**
     * Returns the 10-digit mobile number. Accepts "9876543210", "+91 98765 43210" and "09876543210".
     *
     * @throws IllegalArgumentException for anything else
     */
    public static String normalizeMobile(String raw) {
        if (ServletUtil.isBlank(raw)) {
            throw new IllegalArgumentException("mobile_number zaroori hai");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException("mobile_number 10 digit hona chahiye");
        }
        return digits;
    }

    /** Returns the trimmed, lower-case email. */
    public static String normalizeEmail(String raw) {
        if (ServletUtil.isBlank(raw)) {
            throw new IllegalArgumentException("email zaroori hai");
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email valid nahi hai");
        }
        return email;
    }

    /** True for http(s) URLs and same-site paths; false for javascript:, data: and the like. */
    public static boolean isSafeUrl(String raw) {
        if (ServletUtil.isBlank(raw)) {
            return false;
        }
        String url = raw.trim();
        if (url.startsWith("/")) {
            return !url.startsWith("//");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://")) && url.indexOf(' ') < 0;
    }

    /** Returns the value from {@code allowed} that matches ignoring case, or throws naming the field. */
    public static String oneOf(String field, String value, String... allowed) {
        String trimmed = value == null ? "" : value.trim();
        for (String option : allowed) {
            if (option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        throw new IllegalArgumentException(field + " must be one of: " + String.join(", ", allowed));
    }

    public static void require(String field, String value, String regex, String hint) {
        if (value == null || !value.trim().matches(regex)) {
            throw new IllegalArgumentException(field + " valid nahi hai (" + hint + ")");
        }
    }
}

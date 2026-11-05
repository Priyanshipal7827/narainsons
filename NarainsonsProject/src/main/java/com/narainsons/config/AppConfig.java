package com.narainsons.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AppConfig() {
    }

    public static boolean isAuthEnabled() {
        return Boolean.parseBoolean(PROPS.getProperty("auth.enabled", "false"));
    }

    public static String getAuthApiKey() {
        return PROPS.getProperty("auth.api.key", "");
    }

    public static Set<String> getPublicPaths() {
        String raw = PROPS.getProperty("auth.public.paths", "/api/health");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Postman/testing: OTP send responses mein dev_otp dikhao jab SMS/email chala ho. */
    public static boolean isDevExposeOtp() {
        return Boolean.parseBoolean(PROPS.getProperty("app.dev.expose_otp", "false"));
    }

    public static String getAdminApiKey() {
        return PROPS.getProperty("admin.api.key", "");
    }

    public static String getCronEmiSecret() {
        return PROPS.getProperty("cron.emi.secret", "");
    }

    /** Browser origins allowed to call the API, for a UI hosted elsewhere (cors.allowed.origins). */
    public static Set<String> getCorsAllowedOrigins() {
        return Arrays.stream(PROPS.getProperty("cors.allowed.origins", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Off by default: see docs/loan/EMI-NOTIFICATIONS-API.md and the compliance note in README. */
    public static boolean isContactsGallerySyncEnabled() {
        return Boolean.parseBoolean(PROPS.getProperty("app.sync.contacts_gallery.enabled", "false"));
    }

    public static int getAccessTokenMinutes() {
        return Integer.parseInt(PROPS.getProperty("jwt.access.minutes", "60"));
    }

    public static BigDecimal getLoanInterestAnnualPercent() {
        return new BigDecimal(PROPS.getProperty("loan.interest.annual_percent", "0").trim());
    }

    public static BigDecimal getLoanProcessingFeePercent() {
        return new BigDecimal(PROPS.getProperty("loan.processing_fee.percent", "0").trim());
    }

    public static boolean isKycRequiredForApproval() {
        return Boolean.parseBoolean(PROPS.getProperty("loan.approve.require_kyc_verified", "true"));
    }

    public static boolean isReminderSchedulerEnabled() {
        return Boolean.parseBoolean(PROPS.getProperty("emi.reminder.scheduler.enabled", "true"));
    }

    /** Where user uploads live. Set uploads.dir on a server so redeploys cannot touch them. */
    public static Path getUserFilesRoot() {
        String configured = PROPS.getProperty("uploads.dir", "").trim();
        if (!configured.isEmpty()) {
            return Path.of(configured);
        }
        String base = System.getProperty("catalina.base", System.getProperty("user.home", "."));
        return Path.of(base, "narainsons-uploads", "user-files");
    }

    public static int getAdminSessionHours() {
        return Integer.parseInt(PROPS.getProperty("session.admin.hours", "12"));
    }

    public static int getUserSessionDays() {
        return Integer.parseInt(PROPS.getProperty("session.user.days", "30"));
    }

    /** Minimum gap between two reminders for the same EMI. The cron may still run every hour. */
    public static int getEmiReminderMinHours() {
        return Integer.parseInt(PROPS.getProperty("emi.reminder.min_hours", "24"));
    }

    public static int getEmiReminderDaysBefore() {
        return Integer.parseInt(PROPS.getProperty("emi.reminder.days_before", "3"));
    }
}

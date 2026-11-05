package com.narainsons.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.narainsons.config.AppConfig;
import com.narainsons.dao.DeviceDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.util.ServletUtil;
import com.narainsons.util.Validators;

public class OnboardingService {

    private static final Set<String> STEP1 = Set.of(
            "name", "first_name", "last_name", "email", "gender", "date_of_birth", "age",
            "marital_status", "nationality", "profile_image", "alternate_mobile_number");
    private static final Set<String> STEP2 = Set.of(
            "current_address", "current_city", "current_state", "current_pincode", "current_country",
            "permanent_address", "permanent_city", "permanent_state", "permanent_pincode", "permanent_country",
            "same_as_current_address", "employment_type", "company_name", "designation",
            "work_experience_months", "monthly_income", "annual_income", "income_source");
    private static final Set<String> STEP3 = Set.of(
            "aadhaar_encrypted", "aadhaar_last4", "pan_encrypted", "pan_last4",
            "aadhaar_document", "pan_document", "bank_name", "ifsc_code", "bank_account_encrypted",
            "bank_account_last4",
            "privacy_consent", "terms_accepted", "kyc_consent");
    private static final Set<String> SYNC_FIELDS = Set.of(
            "device_id", "device_type", "device_name", "device_model", "os_version", "app_version",
            "time_zone", "latitude", "longitude", "fcm_token", "notification_enable", "notification_disable_at",
            "is_app_installed", "application_install_time", "application_uninstall_time", "is_login");

    private final UserDAO userDAO = new UserDAO();

    public User saveStep(User user, int step, JsonObject json) throws SQLException {
        if (step < 1 || step > 3) {
            throw new IllegalArgumentException("step 1, 2 ya 3 hona chahiye");
        }
        int completed = user.getOnboardingStep() == null ? 0 : user.getOnboardingStep();
        if (step > completed + 1) {
            throw new IllegalArgumentException("Pehle step " + (completed + 1) + " complete karo");
        }
        validateRequired(step, json);
        String emailBefore = user.getEmail();
        applyFields(user, json, stepFields(step));
        validateValues(user, step);
        if (step == 1 && !user.getEmail().equalsIgnoreCase(emailBefore == null ? "" : emailBefore)) {
            user.setEmailVerifiedAt(null);
        }
        if (step == 1 && ServletUtil.isBlank(user.getCustomerCode())) {
            user.setCustomerCode("CUST" + user.getId() + System.currentTimeMillis());
        }
        stampConsents(user, step);
        if (step == 3) {
            // Bank details just came from the user, so they are unverified until checked by staff.
            user.setBankVerificationStatus("pending");
            user.setBankVerifiedAt(null);
        }
        user.setOnboardingStep(Math.max(step, user.getOnboardingStep() == null ? 0 : user.getOnboardingStep()));
        if (step == 3) {
            user.setKycStatus("submitted");
            user.setKycVerifiedAt(null);
            user.setKycRejectionReason(null);
        }
        userDAO.update(user);
        if (step == 3) {
            new AdminAlertService().raise("kyc_submitted", "KYC submitted",
                    (ServletUtil.isBlank(user.getName()) ? "A customer" : user.getName()) + " submitted KYC for review",
                    "admin-user-view.html?id=" + user.getId());
        }
        return userDAO.findById(user.getId()).orElse(user);
    }

    public User saveDeviceSync(User user, JsonObject json) throws SQLException {
        if (AppConfig.isContactsGallerySyncEnabled()) {
            if (json.has("contacts")) {
                user.setContactsData(json.get("contacts").toString());
            }
            if (json.has("gallery")) {
                user.setGalleryData(json.get("gallery").toString());
            }
        }
        UserMergeLite.apply(user, json, SYNC_FIELDS);
        if (json.has("fcm_token") && !ServletUtil.isBlank(user.getFcmToken())) {
            user.setFcmTokenUpdatedAt(LocalDateTime.now());
            String platform = user.getDeviceType() == null ? "" : user.getDeviceType().trim().toLowerCase();
            new DeviceDAO().upsert(user.getId(), user.getFcmToken().trim(),
                    "ios".equals(platform) || "web".equals(platform) ? platform : "android",
                    user.getDeviceId(), user.getAppVersion());
        }
        if (Boolean.TRUE.equals(user.getIsAppInstalled()) && user.getApplicationInstallTime() == null) {
            user.setApplicationInstallTime(LocalDateTime.now());
        }
        userDAO.updateSyncPayload(user.getId(), user.getContactsData(), user.getGalleryData());
        userDAO.update(user);
        return userDAO.findById(user.getId()).orElse(user);
    }

    public static boolean isProfileComplete(User user) {
        return !ServletUtil.isBlank(user.getName())
                && !ServletUtil.isBlank(user.getEmail())
                && !ServletUtil.isBlank(user.getCurrentAddress())
                && !ServletUtil.isBlank(user.getEmploymentType())
                && Boolean.TRUE.equals(user.getPrivacyConsent())
                && Boolean.TRUE.equals(user.getTermsAccepted());
    }

    /** Runs after merging, so it also covers optional fields sent in this step. */
    private static void validateValues(User user, int step) {
        if (step == 1) {
            user.setEmail(Validators.normalizeEmail(user.getEmail()));
            user.setGender(Validators.oneOf("gender", user.getGender(), "Male", "Female", "Other"));
            if (user.getDateOfBirth().isAfter(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("date_of_birth future mein nahi ho sakti");
            }
            if (!ServletUtil.isBlank(user.getMaritalStatus())) {
                user.setMaritalStatus(Validators.oneOf("marital_status", user.getMaritalStatus(),
                        "Single", "Married", "Divorced", "Widowed"));
            }
            if (!ServletUtil.isBlank(user.getAlternateMobileNumber())) {
                user.setAlternateMobileNumber(Validators.normalizeMobile(user.getAlternateMobileNumber()));
            }
        } else if (step == 2) {
            Validators.require("current_pincode", user.getCurrentPincode(), "\\d{6}", "6 digit");
            user.setEmploymentType(Validators.oneOf("employment_type", user.getEmploymentType(),
                    "Salaried", "Self Employed", "Business", "Professional", "Unemployed", "Other"));
            if (user.getMonthlyIncome().signum() < 0) {
                throw new IllegalArgumentException("monthly_income negative nahi ho sakti");
            }
        } else if (step == 3) {
            Validators.require("aadhaar_last4", user.getAadhaarLast4(), "\\d{4}", "4 digit");
            Validators.require("pan_last4", user.getPanLast4(), "[A-Za-z0-9]{4}", "4 characters");
            Validators.require("bank_account_last4", user.getBankAccountLast4(), "\\d{4}", "4 digit");
            Validators.require("ifsc_code", user.getIfscCode(), "[A-Za-z]{4}0[A-Za-z0-9]{6}", "jaise HDFC0001234");
            user.setIfscCode(user.getIfscCode().trim().toUpperCase());
            requireSafeFile("aadhaar_document", user.getAadhaarDocument());
            requireSafeFile("pan_document", user.getPanDocument());
        }
    }

    private static void requireSafeFile(String field, String url) {
        if (!ServletUtil.isBlank(url) && !Validators.isSafeUrl(url)) {
            throw new IllegalArgumentException(field + " must be the url returned by the upload API");
        }
    }

    private static Set<String> stepFields(int step) {
        return switch (step) {
            case 1 -> STEP1;
            case 2 -> STEP2;
            default -> STEP3;
        };
    }

    private static void validateRequired(int step, JsonObject json) {
        for (String field : requiredFor(step)) {
            if (!json.has(field) || json.get(field).isJsonNull()
                    || (json.get(field).isJsonPrimitive() && json.get(field).getAsString().isBlank())) {
                throw new IllegalArgumentException("Step " + step + " — field missing: " + field);
            }
        }
    }

    private static Set<String> requiredFor(int step) {
        return switch (step) {
            case 1 -> Set.of("name", "first_name", "last_name", "email", "gender", "date_of_birth");
            case 2 -> Set.of("current_address", "current_city", "current_state", "current_pincode",
                    "employment_type", "monthly_income");
            case 3 -> Set.of("aadhaar_last4", "pan_last4", "bank_name", "ifsc_code", "bank_account_last4",
                    "privacy_consent", "terms_accepted", "kyc_consent");
            default -> Set.of();
        };
    }

    private static void applyFields(User user, JsonObject json, Set<String> allowed) {
        UserMergeLite.apply(user, json, allowed);
    }

    private static void stampConsents(User user, int step) {
        LocalDateTime now = LocalDateTime.now();
        if (step >= 3) {
            if (Boolean.TRUE.equals(user.getPrivacyConsent())) {
                user.setPrivacyConsentAt(now);
            }
            if (Boolean.TRUE.equals(user.getTermsAccepted())) {
                user.setTermsAcceptedAt(now);
            }
            if (Boolean.TRUE.equals(user.getKycConsent())) {
                user.setKycConsentAt(now);
            }
        }
    }

    private static String normalizeGender(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "male", "m" -> "Male";
            case "female", "f" -> "Female";
            case "other", "o" -> "Other";
            default -> {
                if (value.equals("Male") || value.equals("Female") || value.equals("Other")) {
                    yield value;
                }
                yield Character.toUpperCase(v.charAt(0)) + v.substring(1);
            }
        };
    }

    static final class UserMergeLite {
        private UserMergeLite() {
        }

        static void apply(User user, JsonObject json, Set<String> allowed) {
            for (String key : allowed) {
                if (!json.has(key) || json.get(key).isJsonNull()) {
                    continue;
                }
                JsonElement el = json.get(key);
                try {
                    applyOne(user, key, el);
                } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException
                        | java.time.DateTimeException e) {
                    throw new IllegalArgumentException(key + " ki value valid nahi hai");
                }
            }
        }

        private static void applyOne(User user, String key, JsonElement el) {
                switch (key) {
                    case "same_as_current_address", "privacy_consent", "terms_accepted", "kyc_consent",
                            "notification_enable", "is_app_installed", "is_login" ->
                        userSetterBool(user, key, el.getAsBoolean());
                    case "work_experience_months", "age" -> {
                        if ("age".equals(key)) {
                            user.setAge(el.getAsInt());
                        } else {
                            user.setWorkExperienceMonths(el.getAsInt());
                        }
                    }
                    case "monthly_income" -> user.setMonthlyIncome(el.getAsBigDecimal());
                    case "annual_income" -> user.setAnnualIncome(el.getAsBigDecimal());
                    case "latitude" -> user.setLatitude(el.getAsBigDecimal());
                    case "longitude" -> user.setLongitude(el.getAsBigDecimal());
                    case "date_of_birth" -> user.setDateOfBirth(java.time.LocalDate.parse(el.getAsString()));
                    case "application_install_time", "application_uninstall_time", "notification_disable_at" ->
                        userSetterDateTime(user, key, LocalDateTime.parse(el.getAsString()));
                    default -> userSetterString(user, key, el.getAsString());
                }
        }

        private static void userSetterString(User user, String key, String value) {
            switch (key) {
                case "name" -> user.setName(value);
                case "first_name" -> user.setFirstName(value);
                case "last_name" -> user.setLastName(value);
                case "email" -> user.setEmail(value);
                case "gender" -> user.setGender(normalizeGender(value));
                case "marital_status" -> user.setMaritalStatus(value);
                case "nationality" -> user.setNationality(value);
                case "profile_image" -> user.setProfileImage(value);
                case "alternate_mobile_number" -> user.setAlternateMobileNumber(value);
                case "current_address" -> user.setCurrentAddress(value);
                case "current_city" -> user.setCurrentCity(value);
                case "current_state" -> user.setCurrentState(value);
                case "current_pincode" -> user.setCurrentPincode(value);
                case "current_country" -> user.setCurrentCountry(value);
                case "permanent_address" -> user.setPermanentAddress(value);
                case "permanent_city" -> user.setPermanentCity(value);
                case "permanent_state" -> user.setPermanentState(value);
                case "permanent_pincode" -> user.setPermanentPincode(value);
                case "permanent_country" -> user.setPermanentCountry(value);
                case "employment_type" -> user.setEmploymentType(value);
                case "company_name" -> user.setCompanyName(value);
                case "designation" -> user.setDesignation(value);
                case "income_source" -> user.setIncomeSource(value);
                case "aadhaar_encrypted" -> user.setAadhaarEncrypted(value);
                case "aadhaar_last4" -> user.setAadhaarLast4(value);
                case "pan_encrypted" -> user.setPanEncrypted(value);
                case "pan_last4" -> user.setPanLast4(value);
                case "aadhaar_document" -> user.setAadhaarDocument(value);
                case "pan_document" -> user.setPanDocument(value);
                case "bank_name" -> user.setBankName(value);
                case "ifsc_code" -> user.setIfscCode(value);
                case "bank_account_encrypted" -> user.setBankAccountEncrypted(value);
                case "bank_account_last4" -> user.setBankAccountLast4(value);
                case "bank_verification_status" -> user.setBankVerificationStatus(value);
                case "device_id" -> user.setDeviceId(value);
                case "device_type" -> user.setDeviceType(value);
                case "device_name" -> user.setDeviceName(value);
                case "device_model" -> user.setDeviceModel(value);
                case "os_version" -> user.setOsVersion(value);
                case "app_version" -> user.setAppVersion(value);
                case "time_zone" -> user.setTimeZone(value);
                case "fcm_token" -> user.setFcmToken(value);
                default -> { }
            }
        }

        private static void userSetterBool(User user, String key, boolean value) {
            switch (key) {
                case "same_as_current_address" -> user.setSameAsCurrentAddress(value);
                case "privacy_consent" -> user.setPrivacyConsent(value);
                case "terms_accepted" -> user.setTermsAccepted(value);
                case "kyc_consent" -> user.setKycConsent(value);
                case "notification_enable" -> user.setNotificationEnable(value);
                case "is_app_installed" -> user.setIsAppInstalled(value);
                case "is_login" -> user.setIsLogin(value);
                default -> { }
            }
        }

        private static void userSetterDateTime(User user, String key, LocalDateTime value) {
            switch (key) {
                case "application_install_time" -> user.setApplicationInstallTime(value);
                case "application_uninstall_time" -> user.setApplicationUninstallTime(value);
                case "notification_disable_at" -> user.setNotificationDisableAt(value);
                default -> { }
            }
        }
    }
}

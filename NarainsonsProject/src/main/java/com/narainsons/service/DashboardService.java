package com.narainsons.service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.narainsons.config.AppConfig;
import com.narainsons.config.LoanConfig;
import com.narainsons.dao.BannerDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.dao.LoanApplyGuideVideoDAO;
import com.narainsons.dao.LoanGuarantorDAO;
import com.narainsons.model.DashboardBanner;
import com.narainsons.model.LoanApplication;
import com.narainsons.model.LoanApplyGuideVideo;
import com.narainsons.model.LoanGuarantor;
import com.narainsons.model.User;
import com.narainsons.util.ServletUtil;

public class DashboardService {

    private final BannerDAO bannerDAO = new BannerDAO();
    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();
    private final LoanGuarantorDAO guarantorDAO = new LoanGuarantorDAO();
    private final LoanApplyGuideVideoDAO guideVideoDAO = new LoanApplyGuideVideoDAO();

    public Map<String, Object> buildHome(User user) throws SQLException {
        boolean profileComplete = OnboardingService.isProfileComplete(user);
        List<DashboardBanner> banners = bannerDAO.findActiveForDashboard();
        LoanApplication latest = loanDAO.findLatestByUser(user.getId()).orElse(null);

        Map<String, Object> data = new HashMap<>();
        data.put("profile_complete", profileComplete);
        data.put("onboarding_step", user.getOnboardingStep() == null ? 0 : user.getOnboardingStep());
        data.put("user", ServletUtil.userView(user));
        data.put("emi_summary", new EmiService().summary(user));
        data.put("banners", banners.stream().map(this::bannerPayload).collect(Collectors.toList()));
        data.put("loan_config", loanConfigPayload());
        data.put("how_to_apply_loan_video", loadHowToApplyVideo());
        data.put("can_apply_loan", profileComplete && !loanDAO.hasOpenLoanForUser(user.getId()));
        if (latest != null) {
            data.put("latest_loan_application", loanPayloadWithGuarantors(latest));
        }
        return data;
    }

    public List<Map<String, Object>> listBanners() throws SQLException {
        return bannerDAO.findActiveForDashboard().stream().map(this::bannerPayload).collect(Collectors.toList());
    }

    public Map<String, Object> loadHowToApplyVideo() throws SQLException {
        return guideVideoDAO.findPrimaryActive()
                .map(DashboardService::guideVideoPayload)
                .orElse(null);
    }

    public List<Map<String, Object>> listHowToApplyVideos() throws SQLException {
        return guideVideoDAO.findAllActive().stream()
                .map(DashboardService::guideVideoPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> loanConfigPayload() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("interest_rate_annual", AppConfig.getLoanInterestAnnualPercent());
        cfg.put("processing_fee_percent", AppConfig.getLoanProcessingFeePercent());
        cfg.put("max_amount", LoanConfig.MAX_AMOUNT);
        cfg.put("min_amount", LoanConfig.MIN_AMOUNT);
        cfg.put("amount_step", LoanConfig.AMOUNT_STEP);
        cfg.put("allowed_amounts", LoanConfig.allowedAmounts());
        cfg.put("min_tenure_months", LoanConfig.MIN_TENURE_MONTHS);
        cfg.put("max_tenure_months", LoanConfig.MAX_TENURE_MONTHS);
        cfg.put("purpose_options", List.of("personal", "business", "medical", "education", "home_repair", "other"));
        cfg.put("guarantor_per_amount", LoanConfig.GUARANTOR_PER_AMOUNT);
        cfg.put("guarantor_rule", "har ₹10,000 par 1 guarantor (10k→1, 20k→2, 50k→5)");
        Map<Integer, Integer> guarantorsByAmount = new HashMap<>();
        for (int amount : LoanConfig.allowedAmounts()) {
            guarantorsByAmount.put(amount, LoanConfig.requiredGuarantorCount(amount));
        }
        cfg.put("guarantors_required_by_amount", guarantorsByAmount);
        cfg.put("guarantor_required_fields", List.of("full_name", "mobile_number", "relation"));
        cfg.put("guarantor_optional_fields", List.of("address", "aadhaar_last4", "pan_last4"));
        cfg.put("relation_options", List.of("father", "mother", "spouse", "brother", "sister", "friend", "colleague", "other"));
        try {
            cfg.put("how_to_apply_video", loadHowToApplyVideo());
        } catch (SQLException e) {
            cfg.put("how_to_apply_video", null);
        }
        return cfg;
    }

    public static Map<String, Object> guideVideoPayload(LoanApplyGuideVideo v) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", v.getId());
        m.put("title", v.getTitle());
        m.put("description", v.getDescription());
        m.put("video_url", v.getVideoUrl());
        m.put("thumbnail_url", v.getThumbnailUrl());
        m.put("video_type", v.getVideoType());
        m.put("duration_seconds", v.getDurationSeconds());
        m.put("locale", v.getLocale());
        return m;
    }

    public Map<String, Object> loanPayloadWithGuarantors(LoanApplication app) throws SQLException {
        List<LoanGuarantor> guarantors = guarantorDAO.findByLoanApplicationId(app.getId());
        return loanPayload(app, guarantors);
    }

    public static Map<String, Object> loanPayload(LoanApplication app) {
        return loanPayload(app, List.of());
    }

    public static Map<String, Object> loanPayload(LoanApplication app, List<LoanGuarantor> guarantors) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", app.getId());
        m.put("application_code", app.getApplicationCode());
        m.put("requested_amount", app.getRequestedAmount());
        int amount = app.getRequestedAmount() == null ? 0 : app.getRequestedAmount().intValue();
        m.put("guarantors_required", LoanConfig.requiredGuarantorCount(amount));
        m.put("tenure_months", app.getTenureMonths());
        m.put("purpose", app.getPurpose());
        m.put("purpose_detail", app.getPurposeDetail());
        m.put("status", app.getStatus());
        m.put("status_message", app.getStatusMessage());
        m.put("created_at", app.getCreatedAt());
        m.put("updated_at", app.getUpdatedAt());
        m.put("reviewed_at", app.getReviewedAt());
        m.put("interest_rate_annual", app.getInterestRateAnnual());
        m.put("processing_fee", app.getProcessingFee());
        m.put("total_payable", app.getTotalPayable());
        m.put("emi_amount", app.getEmiAmount());
        m.put("disbursed_at", app.getDisbursedAt());
        m.put("disbursement_reference", app.getDisbursementReference());
        m.put("closed_at", app.getClosedAt());
        m.put("guarantors", guarantors.stream().map(DashboardService::guarantorPayload).collect(Collectors.toList()));
        return m;
    }

    public static Map<String, Object> guarantorPayload(LoanGuarantor g) {
        Map<String, Object> m = new HashMap<>();
        m.put("index", g.getGuarantorIndex());
        m.put("full_name", g.getFullName());
        m.put("mobile_number", g.getMobileNumber());
        m.put("relation", g.getRelation());
        m.put("address", g.getAddress());
        m.put("aadhaar_last4", g.getAadhaarLast4());
        m.put("pan_last4", g.getPanLast4());
        return m;
    }

    private Map<String, Object> bannerPayload(DashboardBanner b) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("subtitle", b.getSubtitle());
        m.put("image_url", b.getImageUrl());
        m.put("action_type", b.getActionType());
        m.put("action_value", b.getActionValue());
        m.put("sort_order", b.getSortOrder());
        return m;
    }
}

package com.narainsons.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.narainsons.config.AppConfig;
import com.narainsons.config.LoanConfig;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.dao.LoanGuarantorDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.LoanApplication;
import com.narainsons.model.LoanGuarantor;
import com.narainsons.model.User;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;
import com.narainsons.util.JsonBody;
import com.narainsons.util.ServletUtil;

public class LoanApplicationService {

    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();
    private final LoanGuarantorDAO guarantorDAO = new LoanGuarantorDAO();
    private final UserDAO userDAO = new UserDAO();
    private final DashboardService dashboardService = new DashboardService();

    public Map<String, Object> apply(User user, JsonObject json) throws SQLException {
        if ("rejected".equalsIgnoreCase(user.getKycStatus())) {
            throw ApiException.conflict("KYC_REJECTED", "KYC reject ho gaya hai. Pehle KYC dobara submit karo (onboarding step 3).");
        }
        if (!OnboardingService.isProfileComplete(user)) {
            throw new IllegalArgumentException(
                    "Pehle profile complete karo (onboarding steps 1–3). Uske baad loan apply kar sakte ho.");
        }
        BigDecimal amount = parseAmount(json);
        int amountInt = amount.intValue();
        int requiredGuarantors = LoanConfig.requiredGuarantorCount(amountInt);
        List<LoanGuarantor> guarantors = parseGuarantors(json, requiredGuarantors);

        int tenure = parseTenure(json);
        String purpose = requireString(json, "purpose");
        String purposeDetail = JsonBody.string(json, "purpose_detail", "purposeDetail");

        String bankName = coalesce(JsonBody.string(json, "bank_name", "bankName"), user.getBankName());
        String ifsc = coalesce(JsonBody.string(json, "ifsc_code", "ifscCode"), user.getIfscCode());
        String accountLast4 = coalesce(JsonBody.string(json, "bank_account_last4", "bankAccountLast4"),
                user.getBankAccountLast4());

        if (ServletUtil.isBlank(bankName) || ServletUtil.isBlank(ifsc) || ServletUtil.isBlank(accountLast4)) {
            throw new IllegalArgumentException(
                    "bank_name, ifsc_code, bank_account_last4 zaroori hain (profile ya apply body mein).");
        }

        BigDecimal monthlyIncome = user.getMonthlyIncome();
        String incomeText = JsonBody.string(json, "monthly_income", "monthlyIncome");
        if (!ServletUtil.isBlank(incomeText)) {
            monthlyIncome = new BigDecimal(incomeText.trim());
        }
        if (monthlyIncome == null) {
            throw new IllegalArgumentException("monthly_income profile ya request mein honi chahiye.");
        }

        String employmentType = coalesce(JsonBody.string(json, "employment_type", "employmentType"),
                user.getEmploymentType());
        String companyName = coalesce(JsonBody.string(json, "company_name", "companyName"), user.getCompanyName());

        LoanApplication app = new LoanApplication();
        app.setUserId(user.getId());
        app.setApplicationCode("LN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        app.setRequestedAmount(amount);
        app.setTenureMonths(tenure);
        app.setPurpose(purpose.trim().toLowerCase());
        app.setPurposeDetail(purposeDetail);
        app.setMonthlyIncome(monthlyIncome);
        app.setEmploymentType(employmentType);
        app.setCompanyName(companyName);
        app.setBankName(bankName);
        app.setIfscCode(ifsc.trim().toUpperCase());
        app.setBankAccountLast4(accountLast4);
        app.setStatus("pending");
        app.setStatusMessage(LoanConfig.PENDING_MESSAGE);
        LoanCalculator.Quote quote = LoanCalculator.quote(amount, tenure,
                AppConfig.getLoanInterestAnnualPercent(), AppConfig.getLoanProcessingFeePercent());
        app.setInterestRateAnnual(quote.annualRatePercent());
        app.setProcessingFee(quote.processingFee());
        app.setTotalPayable(quote.totalPayable());
        app.setEmiAmount(quote.emi());

        // User row lock: two parallel requests cannot both pass the open-loan check.
        long id = DBConnection.inTransaction(() -> {
            userDAO.lockForUpdate(user.getId());
            if (loanDAO.hasPendingForUser(user.getId())) {
                throw new IllegalArgumentException(
                        "Ek pending application pehle se hai. Admin approve hone ka wait karo ya status dekho.");
            }
            if (loanDAO.hasOpenLoanForUser(user.getId())) {
                throw new IllegalArgumentException(
                        "Aapka ek loan abhi chal raha hai. Saari EMI bharne ke baad naya loan apply kar sakte ho.");
            }
            long newId = loanDAO.create(app);
            guarantorDAO.insertAll(newId, guarantors);
            return newId;
        });
        new AdminAlertService().raise("new_loan", "New loan application",
                (ServletUtil.isBlank(user.getName()) ? "A customer" : user.getName()) + " applied for ₹" + amount.toPlainString()
                        + " over " + tenure + " months",
                "admin-loan-view.html?id=" + id);
        LoanApplication saved = loanDAO.findByIdAndUser(id, user.getId()).orElseThrow();
        List<LoanGuarantor> savedGuarantors = guarantorDAO.findByLoanApplicationId(id);

        Map<String, Object> data = new HashMap<>();
        data.put("loan_application", DashboardService.loanPayload(saved, savedGuarantors));
        data.put("message", LoanConfig.PENDING_MESSAGE);
        return data;
    }

    /** What a loan would cost, before applying. Same numbers apply() saves. */
    public Map<String, Object> quote(JsonObject json) {
        BigDecimal amount = parseAmount(json);
        int tenure = parseTenure(json);
        LoanCalculator.Quote q = LoanCalculator.quote(amount, tenure,
                AppConfig.getLoanInterestAnnualPercent(), AppConfig.getLoanProcessingFeePercent());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requested_amount", q.principal());
        data.put("tenure_months", q.months());
        data.put("interest_rate_annual", q.annualRatePercent());
        data.put("processing_fee", q.processingFee());
        data.put("net_disbursal", q.netDisbursal());
        data.put("emi_amount", q.emi());
        data.put("last_emi_amount", q.installments().get(q.installments().size() - 1));
        data.put("total_interest", q.totalInterest());
        data.put("total_payable", q.totalPayable());
        data.put("guarantors_required", LoanConfig.requiredGuarantorCount(amount.intValue()));
        return data;
    }

    public Map<String, Object> cancel(User user, long applicationId) throws SQLException {
        loanDAO.findByIdAndUser(applicationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Loan application nahi mila"));
        if (!loanDAO.cancelPending(applicationId, user.getId(), "Application aapne cancel kar di.")) {
            throw ApiException.conflict("LOAN_NOT_PENDING", "Sirf pending application cancel ho sakti hai");
        }
        return getOne(user, applicationId);
    }

    public List<Map<String, Object>> listForUser(User user, int limit, int offset) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LoanApplication app : loanDAO.listByUser(user.getId(), limit, offset)) {
            out.add(dashboardService.loanPayloadWithGuarantors(app));
        }
        return out;
    }

    public Map<String, Object> getOne(User user, long applicationId) throws SQLException {
        LoanApplication app = loanDAO.findByIdAndUser(applicationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Loan application nahi mila"));
        return dashboardService.loanPayloadWithGuarantors(app);
    }

    private static List<LoanGuarantor> parseGuarantors(JsonObject json, int requiredCount) {
        if (!json.has("guarantors") || !json.get("guarantors").isJsonArray()) {
            throw new IllegalArgumentException(
                    "guarantors array zaroori hai — ₹" + LoanConfig.GUARANTOR_PER_AMOUNT
                            + " ke hisaab se " + requiredCount + " guarantor bhejo.");
        }
        JsonArray arr = json.getAsJsonArray("guarantors");
        if (arr.size() != requiredCount) {
            throw new IllegalArgumentException(
                    "Is loan amount ke liye " + requiredCount + " guarantor chahiye, aapne " + arr.size() + " bheje.");
        }
        List<LoanGuarantor> list = new ArrayList<>();
        Set<String> mobiles = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("guarantors[" + i + "] object hona chahiye.");
            }
            JsonObject g = el.getAsJsonObject();
            String name = requireGuarantorField(g, "full_name", "fullName");
            String mobile = normalizeMobile(requireGuarantorField(g, "mobile_number", "mobileNumber"));
            String relation = requireGuarantorField(g, "relation", null);
            if (!mobiles.add(mobile)) {
                throw new IllegalArgumentException("Har guarantor ka alag mobile_number hona chahiye.");
            }
            LoanGuarantor gu = new LoanGuarantor();
            gu.setGuarantorIndex(i + 1);
            gu.setFullName(name.trim());
            gu.setMobileNumber(mobile);
            gu.setRelation(relation.trim().toLowerCase());
            gu.setAddress(JsonBody.string(g, "address", null));
            gu.setAadhaarLast4(JsonBody.string(g, "aadhaar_last4", "aadhaarLast4"));
            gu.setPanLast4(JsonBody.string(g, "pan_last4", "panLast4"));
            list.add(gu);
        }
        return list;
    }

    private static String requireGuarantorField(JsonObject g, String snake, String camel) {
        String v = JsonBody.string(g, snake, camel);
        if (ServletUtil.isBlank(v)) {
            throw new IllegalArgumentException("guarantor." + snake + " zaroori hai.");
        }
        return v;
    }

    private static String normalizeMobile(String mobile) {
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException("guarantor mobile_number 10 digit hona chahiye.");
        }
        return digits;
    }

    private static BigDecimal parseAmount(JsonObject json) {
        String raw = JsonBody.string(json, "requested_amount", "requestedAmount");
        if (ServletUtil.isBlank(raw)) {
            throw new IllegalArgumentException("requested_amount zaroori hai (10000 se 50000, step 5000).");
        }
        int amount;
        try {
            amount = new BigDecimal(raw.trim()).intValueExact();
        } catch (Exception e) {
            throw new IllegalArgumentException("requested_amount valid number hona chahiye.");
        }
        if (amount < LoanConfig.MIN_AMOUNT || amount > LoanConfig.MAX_AMOUNT) {
            throw new IllegalArgumentException(
                    "Loan amount " + LoanConfig.MIN_AMOUNT + " se " + LoanConfig.MAX_AMOUNT + " ke beech honi chahiye.");
        }
        if (amount % LoanConfig.AMOUNT_STEP != 0) {
            throw new IllegalArgumentException(
                    "Amount " + LoanConfig.AMOUNT_STEP + " ke multiples mein honi chahiye (jaise 10000, 15000, 20000).");
        }
        return BigDecimal.valueOf(amount);
    }

    private static int parseTenure(JsonObject json) {
        String raw = JsonBody.string(json, "tenure_months", "tenureMonths");
        if (ServletUtil.isBlank(raw)) {
            throw new IllegalArgumentException("tenure_months zaroori hai.");
        }
        int tenure;
        try {
            tenure = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("tenure_months valid integer hona chahiye.");
        }
        if (tenure < LoanConfig.MIN_TENURE_MONTHS || tenure > LoanConfig.MAX_TENURE_MONTHS) {
            throw new IllegalArgumentException(
                    "tenure_months " + LoanConfig.MIN_TENURE_MONTHS + " se " + LoanConfig.MAX_TENURE_MONTHS + " ke beech.");
        }
        return tenure;
    }

    private static String requireString(JsonObject json, String snakeKey) {
        String v = JsonBody.string(json, snakeKey, toCamel(snakeKey));
        if (ServletUtil.isBlank(v)) {
            throw new IllegalArgumentException(snakeKey + " zaroori hai.");
        }
        return v;
    }

    private static String toCamel(String snake) {
        if (!snake.contains("_")) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                up = true;
            } else if (up) {
                sb.append(Character.toUpperCase(c));
                up = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String coalesce(String a, String b) {
        return ServletUtil.isBlank(a) ? b : a;
    }
}

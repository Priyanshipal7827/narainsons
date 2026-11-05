package com.narainsons.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.narainsons.dao.EmiCollectionSettingsDAO;
import com.narainsons.dao.EmiPaymentDAO;
import com.narainsons.dao.EmiScheduleDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.model.LoanApplication;
import com.narainsons.model.User;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;
import com.narainsons.util.JsonBody;
import com.narainsons.util.Validators;
import com.narainsons.util.ServletUtil;

public class EmiService {

    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();
    private final EmiScheduleDAO scheduleDAO = new EmiScheduleDAO();
    private final EmiPaymentDAO paymentDAO = new EmiPaymentDAO();
    private final EmiCollectionSettingsDAO settingsDAO = new EmiCollectionSettingsDAO();
    private final NotificationService notifications = new NotificationService();

    public void generateScheduleIfMissing(LoanApplication loan) throws SQLException {
        if (!"approved".equals(loan.getStatus()) && !"disbursed".equals(loan.getStatus())) {
            return;
        }
        if (scheduleDAO.existsForLoan(loan.getId())) {
            return;
        }
        int months = loan.getTenureMonths();
        BigDecimal rate = loan.getInterestRateAnnual() == null ? BigDecimal.ZERO : loan.getInterestRateAnnual();
        List<BigDecimal> amounts = LoanCalculator.installments(loan.getRequestedAmount(), months, rate);
        LocalDate firstDue = LocalDate.now().plusMonths(1);
        List<EmiScheduleDAO.EmiRow> rows = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            rows.add(new EmiScheduleDAO.EmiRow(
                    0, loan.getId(), loan.getUserId(), i,
                    firstDue.plusMonths(i - 1), amounts.get(i - 1), BigDecimal.ZERO, "pending", null, null));
        }
        scheduleDAO.insertBatch(rows);
    }

    public Map<String, Object> summary(User user) throws SQLException {
        LoanApplication loan = loanDAO.findLatestApprovedByUser(user.getId()).orElse(null);
        Map<String, Object> data = new HashMap<>();
        if (loan == null) {
            data.put("has_active_loan", false);
            return data;
        }
        generateScheduleIfMissing(loan);
        data.put("has_active_loan", true);
        data.put("loan_application_id", loan.getId());
        data.put("application_code", loan.getApplicationCode());
        data.put("loan_status", loan.getStatus());
        data.put("can_pay", "disbursed".equals(loan.getStatus()));
        data.put("disbursed_at", loan.getDisbursedAt());
        data.put("total_amount", loan.getRequestedAmount());
        data.put("total_payable", loan.getTotalPayable());
        int pending = scheduleDAO.countPending(user.getId(), loan.getId());
        List<EmiScheduleDAO.EmiRow> all = scheduleDAO.listByUser(user.getId(), loan.getId());
        data.put("total_installments", all.size());
        data.put("remaining_installments", pending);
        data.put("paid_installments", all.size() - pending);
        data.put("outstanding_amount", all.stream().filter(e -> !"paid".equals(e.status()))
                .map(EmiScheduleDAO.EmiRow::amountDue).reduce(BigDecimal.ZERO, BigDecimal::add));
        all.stream().filter(e -> "pending".equals(e.status()) || "overdue".equals(e.status())
                || "awaiting_proof".equals(e.status())).findFirst().ifPresent(next -> {
            data.put("next_emi", emiPayload(next));
        });
        return data;
    }

    /** loanId null means the current loan; passing one also opens a closed loan's history. */
    public Map<String, Object> schedule(User user, Long loanId) throws SQLException {
        LoanApplication loan = (loanId == null
                ? loanDAO.findLatestApprovedByUser(user.getId())
                : loanDAO.findByIdAndUser(loanId, user.getId()))
                .orElseThrow(() -> ApiException.notFound("Approved loan nahi mila"));
        generateScheduleIfMissing(loan);
        List<EmiScheduleDAO.EmiRow> rows = scheduleDAO.listByUser(user.getId(), loan.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("loan_application_id", loan.getId());
        data.put("loan_status", loan.getStatus());
        data.put("installments", rows.stream().map(EmiService::emiPayload).toList());
        data.put("remaining_installments", scheduleDAO.countPending(user.getId(), loan.getId()));
        return data;
    }

    public Map<String, Object> paymentInstructions() throws SQLException {
        Map<String, Object> data = new HashMap<>();
        data.put("payment_methods", settingsDAO.findActive().orElse(Map.of()));
        data.put("instructions_hindi",
                "UPI / QR / bank transfer se EMI pay karo, phir payment ka screenshot upload karo. Admin verify ke baad EMI paid mark hogi.");
        return data;
    }

    public Map<String, Object> submitPayment(User user, JsonObject json) throws SQLException {
        String emiIdText = JsonBody.string(json, "emi_schedule_id", "emiScheduleId");
        if (ServletUtil.isBlank(emiIdText)) {
            throw new IllegalArgumentException("emi_schedule_id zaroori hai");
        }
        long emiId = Long.parseLong(emiIdText.trim());
        String screenshot = JsonBody.string(json, "screenshot_url", "screenshotUrl");
        if (ServletUtil.isBlank(screenshot)) {
            throw new IllegalArgumentException("screenshot_url zaroori hai (payment proof image URL)");
        }
        if (!Validators.isSafeUrl(screenshot) || screenshot.length() > 1000) {
            throw new IllegalArgumentException("screenshot_url valid http/https URL hona chahiye");
        }
        String amountText = JsonBody.string(json, "amount_claimed", "amountClaimed");
        String utr = JsonBody.string(json, "utr_reference", "utrReference");
        if (utr != null && utr.length() > 100) {
            throw new IllegalArgumentException("utr_reference max 100 characters");
        }

        long payId = DBConnection.inTransaction(() -> {
            // Row lock: two taps on "submit" cannot both pass the pending check.
            EmiScheduleDAO.EmiRow emi = scheduleDAO.findByIdForUserLocked(emiId, user.getId())
                    .orElseThrow(() -> ApiException.notFound("EMI schedule nahi mila"));
            if ("paid".equals(emi.status())) {
                throw new IllegalArgumentException("Yeh EMI pehle se paid hai");
            }
            LoanApplication loan = loanDAO.findById(emi.loanApplicationId()).orElseThrow();
            if (!"disbursed".equals(loan.getStatus())) {
                throw ApiException.conflict("LOAN_NOT_DISBURSED", "Loan abhi disburse nahi hua, EMI baad mein bharna");
            }
            if (paymentDAO.hasPendingForEmi(emiId)) {
                throw new IllegalArgumentException("Is EMI par payment proof pehle se pending hai — admin approval ka wait karo");
            }
            BigDecimal claimed = ServletUtil.isBlank(amountText) ? emi.amountDue() : new BigDecimal(amountText.trim());
            if (claimed.compareTo(emi.amountDue()) < 0) {
                throw new IllegalArgumentException("amount_claimed EMI amount (₹" + emi.amountDue() + ") se kam nahi ho sakta");
            }
            long id = paymentDAO.insert(new EmiPaymentDAO.PaymentRow(
                    0, emiId, user.getId(), emi.loanApplicationId(),
                    claimed, screenshot.trim(), utr, "pending", null, null, null));
            scheduleDAO.markAwaitingProof(emiId);
            notifications.notifyUser(user.getId(), "emi_payment",
                    "EMI payment submitted",
                    "Aapka payment proof admin ko bhej diya gaya. Verify hone par EMI paid mark hogi.",
                    Map.of("emi_payment_id", id, "emi_schedule_id", emiId));
            return id;
        });

        new AdminAlertService().raise("emi_proof", "EMI proof received",
                (ServletUtil.isBlank(user.getName()) ? "A customer" : user.getName()) + " sent proof for an EMI payment",
                "admin-emi-payments.html?status=pending");
        Map<String, Object> data = new HashMap<>();
        data.put("payment_submission_id", payId);
        data.put("status", "pending");
        data.put("message", "Screenshot upload ho gaya. Admin EMI verify karega — tab remaining EMI kam dikhegi.");
        return data;
    }

    public Map<String, Object> getPayment(User user, long paymentId) throws SQLException {
        EmiPaymentDAO.PaymentRow row = paymentDAO.findById(paymentId)
                .filter(p -> p.userId() == user.getId())
                .orElseThrow(() -> ApiException.notFound("Payment nahi mila"));
        return paymentPayload(row);
    }

    public List<Map<String, Object>> listPayments(User user, int limit, int offset) throws SQLException {
        return paymentDAO.listByUser(user.getId(), limit, offset).stream().map(EmiService::paymentPayload).toList();
    }

    public static Map<String, Object> emiPayload(EmiScheduleDAO.EmiRow e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.id());
        m.put("installment_number", e.installmentNumber());
        m.put("due_date", e.dueDate());
        m.put("amount_due", e.amountDue());
        m.put("amount_paid", e.amountPaid());
        m.put("status", e.status());
        m.put("paid_at", e.paidAt());
        return m;
    }

    public static Map<String, Object> paymentPayload(EmiPaymentDAO.PaymentRow p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.id());
        m.put("emi_schedule_id", p.emiScheduleId());
        m.put("amount_claimed", p.amountClaimed());
        m.put("screenshot_url", p.screenshotUrl());
        m.put("utr_reference", p.utrReference());
        m.put("status", p.status());
        m.put("admin_note", p.adminNote());
        m.put("submitted_at", p.submittedAt());
        m.put("reviewed_at", p.reviewedAt());
        return m;
    }
}

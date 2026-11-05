package com.narainsons.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.EmiPaymentDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.util.ServletUtil;
import com.narainsons.dao.EmiScheduleDAO;
import com.narainsons.dao.LoanApplicationDAO;
import com.narainsons.model.LoanApplication;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;

public class AdminLoanEmiService {

    private final LoanApplicationDAO loanDAO = new LoanApplicationDAO();
    private final EmiScheduleDAO scheduleDAO = new EmiScheduleDAO();
    private final EmiPaymentDAO paymentDAO = new EmiPaymentDAO();
    private final EmiService emiService = new EmiService();
    private final NotificationService notifications = new NotificationService();
    private final UserDAO userDAO = new UserDAO();

    public Map<String, Object> approveLoan(long loanId, String adminNote) throws SQLException {
        LoanApplication existing = loanDAO.findById(loanId)
                .orElseThrow(() -> ApiException.notFound("Loan application nahi mila"));
        if (AppConfig.isKycRequiredForApproval()) {
            User applicant = userDAO.findById(existing.getUserId())
                    .orElseThrow(() -> ApiException.notFound("Applicant not found"));
            if (!"verified".equalsIgnoreCase(applicant.getKycStatus())) {
                throw ApiException.conflict("KYC_NOT_VERIFIED", "Verify this applicant's KYC before approving the loan");
            }
        }
        String msg = "Badhai ho! Aapka loan approve ho gaya. Amount jald aapke bank account mein bheja jayega.";
        DBConnection.inTransaction(() -> {
            if (!loanDAO.decidePending(loanId, "approved", msg, adminNote)) {
                throw new IllegalArgumentException("Sirf pending loan approve ho sakta hai");
            }
            LoanApplication loan = loanDAO.findById(loanId).orElseThrow();
            emiService.generateScheduleIfMissing(loan);
            notifications.notifyUser(loan.getUserId(), "loan_status", "Loan approved", msg,
                    Map.of("loan_application_id", loanId, "status", "approved"));
            return null;
        });
        return Map.of("loan_application_id", loanId, "status", "approved");
    }

    /** Money has been sent: EMIs now run from today and become payable. */
    public Map<String, Object> disburseLoan(long loanId, String reference) throws SQLException {
        LoanApplication loan = loanDAO.findById(loanId)
                .orElseThrow(() -> ApiException.notFound("Loan application nahi mila"));
        if (ServletUtil.isBlank(reference) || reference.length() > 100) {
            throw new IllegalArgumentException("disbursement_reference is required (bank/UTR reference, max 100 characters)");
        }
        LocalDateTime now = LocalDateTime.now();
        String msg = "Aapka loan amount bank account mein bhej diya gaya hai. EMI schedule app mein dekhein.";
        DBConnection.inTransaction(() -> {
            if (!loanDAO.markDisbursed(loanId, reference.trim(), msg, now)) {
                throw new IllegalArgumentException("Only an approved loan can be disbursed");
            }
            emiService.generateScheduleIfMissing(loan);
            scheduleDAO.setDueDatesFrom(loanId, now.toLocalDate());
            notifications.notifyUser(loan.getUserId(), "loan_status", "Loan disbursed", msg,
                    Map.of("loan_application_id", loanId, "status", "disbursed"));
            return null;
        });
        return Map.of("loan_application_id", loanId, "status", "disbursed");
    }

    public Map<String, Object> rejectLoan(long loanId, String adminNote) throws SQLException {
        LoanApplication loan = loanDAO.findById(loanId)
                .orElseThrow(() -> ApiException.notFound("Loan application nahi mila"));
        String msg = "Maaf kijiye, aapka loan application approve nahi hua. Support se sampark karein.";
        DBConnection.inTransaction(() -> {
            if (!loanDAO.decidePending(loanId, "rejected", msg, adminNote)) {
                throw new IllegalArgumentException("Sirf pending loan reject ho sakta hai");
            }
            notifications.notifyUser(loan.getUserId(), "loan_status", "Loan not approved", msg,
                    Map.of("loan_application_id", loanId, "status", "rejected"));
            return null;
        });
        return Map.of("loan_application_id", loanId, "status", "rejected");
    }

    public Map<String, Object> approveEmiPayment(long paymentId, String adminNote) throws SQLException {
        EmiPaymentDAO.PaymentRow pay = paymentDAO.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment submission nahi mila"));
        EmiScheduleDAO.EmiRow emi = scheduleDAO.findById(pay.emiScheduleId()).orElseThrow();
        if (pay.amountClaimed().compareTo(emi.amountDue()) < 0) {
            throw new IllegalArgumentException("Claimed amount is less than the EMI due. Reject this payment instead.");
        }
        int remaining = DBConnection.inTransaction(() -> {
            if (!paymentDAO.reviewPending(paymentId, "approved", adminNote)) {
                throw new IllegalArgumentException("Payment pehle se process ho chuka hai");
            }
            scheduleDAO.markPaid(emi.id(), pay.amountClaimed());
            int left = scheduleDAO.countPending(pay.userId(), pay.loanApplicationId());
            notifications.notifyUser(pay.userId(), "emi_payment", "EMI payment approved",
                    "Aapki EMI verify ho gayi. Shukriya! Baaki EMI: " + left,
                    Map.of("emi_payment_id", paymentId, "remaining_installments", left));
            if (left == 0 && loanDAO.markClosed(pay.loanApplicationId(), "Aapka loan poora chuka diya gaya. Shukriya!")) {
                notifications.notifyUser(pay.userId(), "loan_status", "Loan closed",
                        "Saari EMI bhar gayi. Aapka loan close ho gaya hai.",
                        Map.of("loan_application_id", pay.loanApplicationId(), "status", "closed"));
            }
            return left;
        });

        Map<String, Object> data = new HashMap<>();
        data.put("payment_id", paymentId);
        data.put("status", "approved");
        data.put("remaining_installments", remaining);
        return data;
    }

    public Map<String, Object> rejectEmiPayment(long paymentId, String adminNote) throws SQLException {
        EmiPaymentDAO.PaymentRow pay = paymentDAO.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment submission nahi mila"));
        EmiScheduleDAO.EmiRow emi = scheduleDAO.findById(pay.emiScheduleId()).orElseThrow();
        DBConnection.inTransaction(() -> {
            if (!paymentDAO.reviewPending(paymentId, "rejected", adminNote)) {
                throw new IllegalArgumentException("Payment pehle se process ho chuka hai");
            }
            scheduleDAO.updateStatus(emi.id(), emi.dueDate().isBefore(LocalDate.now()) ? "overdue" : "pending");
            notifications.notifyUser(pay.userId(), "emi_payment", "EMI payment rejected",
                    "Payment proof accept nahi hua. Dubara sahi screenshot upload karein. "
                            + (adminNote == null ? "" : adminNote),
                    Map.of("emi_payment_id", paymentId, "status", "rejected"));
            return null;
        });
        return Map.of("payment_id", paymentId, "status", "rejected");
    }

    public List<Map<String, Object>> listPendingPayments(int limit) throws SQLException {
        return paymentDAO.listPendingForAdmin(limit).stream().map(EmiService::paymentPayload).toList();
    }
}

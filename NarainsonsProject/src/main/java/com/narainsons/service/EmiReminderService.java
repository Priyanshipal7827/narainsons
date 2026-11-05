package com.narainsons.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.EmiScheduleDAO;

public class EmiReminderService {

    private final EmiScheduleDAO scheduleDAO = new EmiScheduleDAO();
    private final NotificationService notifications = new NotificationService();

    public Map<String, Object> runHourlyReminders() throws SQLException {
        int daysBefore = AppConfig.getEmiReminderDaysBefore();
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(daysBefore);
        int overdueMarked = scheduleDAO.markOverdue(today);
        List<EmiScheduleDAO.EmiRow> dueList = scheduleDAO.findDueForReminders(windowEnd);

        int sent = 0;
        LocalDateTime now = LocalDateTime.now();

        for (EmiScheduleDAO.EmiRow emi : dueList) {
            if (!shouldSendReminder(emi, now)) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, emi.dueDate());
            String title = "EMI yaad dilana";
            String body = daysLeft < 0
                    ? "Aapki EMI " + (-daysLeft) + " din se overdue hai (₹" + emi.amountDue()
                            + "). Extra charges se bachne ke liye abhi pay karein (UPI/QR/bank)."
                    : daysLeft == 0
                    ? "Aapki EMI aaj due hai ₹" + emi.amountDue()
                            + " — extra charges se bachne ke liye abhi pay karein (UPI/QR/bank)."
                    : "EMI " + daysLeft + " din mein due hai (₹" + emi.amountDue()
                            + "). Extra charges se bachne ke liye time par pay karein.";

            Map<String, Object> payload = new HashMap<>();
            payload.put("emi_schedule_id", emi.id());
            payload.put("due_date", emi.dueDate().toString());
            payload.put("amount_due", emi.amountDue());
            payload.put("type", "emi_reminder");

            notifications.notifyUser(emi.userId(), "emi_reminder", title, body, payload);
            scheduleDAO.touchReminder(emi.id());
            sent++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reminders_sent", sent);
        result.put("overdue_marked", overdueMarked);
        result.put("window_days_before", daysBefore);
        return result;
    }

    private static boolean shouldSendReminder(EmiScheduleDAO.EmiRow emi, LocalDateTime now) {
        if ("paid".equals(emi.status()) || "awaiting_proof".equals(emi.status())) {
            return false;
        }
        if (emi.lastReminderAt() == null) {
            return true;
        }
        long hours = ChronoUnit.HOURS.between(emi.lastReminderAt(), now);
        return hours >= AppConfig.getEmiReminderMinHours();
    }
}

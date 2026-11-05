USE narainson_db;

-- Alerts shown to staff in the admin panel: new user, new loan, KYC submitted, EMI proof received.
CREATE TABLE IF NOT EXISTS admin_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(500) NOT NULL,
    link VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_notif_created (created_at),
    INDEX idx_admin_notif_type (type)
);

-- Each admin's own "read up to here" marker for the bell.
ALTER TABLE admins ADD COLUMN alerts_read_id BIGINT NOT NULL DEFAULT 0;

-- Date filters on the admin lists.
ALTER TABLE users ADD INDEX idx_users_created (created_at);
ALTER TABLE emi_payment_submissions ADD INDEX idx_pay_submitted (submitted_at);
ALTER TABLE loan_emi_schedules ADD INDEX idx_emi_due_status (due_date, status);

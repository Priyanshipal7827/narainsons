USE narainson_db;

CREATE TABLE IF NOT EXISTS emi_collection_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payee_name VARCHAR(150) NOT NULL DEFAULT 'Narainsons Finance',
    upi_id VARCHAR(100) NULL,
    qr_image_url VARCHAR(1000) NULL,
    bank_name VARCHAR(150) NULL,
    bank_account_name VARCHAR(150) NULL,
    bank_account_number VARCHAR(50) NULL,
    ifsc_code VARCHAR(15) NULL,
    payment_note VARCHAR(500) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category ENUM('loan_status','emi_reminder','emi_payment','general') NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    payload_json JSON NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notif_user_created (user_id, created_at DESC),
    INDEX idx_notif_user_unread (user_id, read_at)
);

CREATE TABLE IF NOT EXISTS loan_emi_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_application_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount_due DECIMAL(15,2) NOT NULL,
    amount_paid DECIMAL(15,2) NOT NULL DEFAULT 0,
    status ENUM('pending','paid','overdue','awaiting_proof') NOT NULL DEFAULT 'pending',
    last_reminder_at DATETIME NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_emi_loan FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id),
    CONSTRAINT fk_emi_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_emi_loan_installment (loan_application_id, installment_number),
    INDEX idx_emi_user_due (user_id, due_date, status)
);

CREATE TABLE IF NOT EXISTS emi_payment_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emi_schedule_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    loan_application_id BIGINT NOT NULL,
    amount_claimed DECIMAL(15,2) NOT NULL,
    screenshot_url VARCHAR(1000) NOT NULL,
    utr_reference VARCHAR(100) NULL,
    status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    admin_note TEXT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    CONSTRAINT fk_pay_emi FOREIGN KEY (emi_schedule_id) REFERENCES loan_emi_schedules(id),
    INDEX idx_pay_user_status (user_id, status)
);

INSERT INTO emi_collection_settings (payee_name, upi_id, qr_image_url, bank_name, bank_account_name, bank_account_number, ifsc_code, payment_note, is_active)
SELECT 'Narainsons Finance', 'narainsons@upi', 'https://cdn.example.com/emi-payment-qr.png',
       'HDFC Bank', 'Narainsons Finance Pvt Ltd', 'XXXX1234567890', 'HDFC0001234',
       'EMI pay karte waqt application code note mein likho. Screenshot yahi app se upload karo.',
       1
WHERE NOT EXISTS (SELECT 1 FROM emi_collection_settings WHERE is_active = 1 LIMIT 1);

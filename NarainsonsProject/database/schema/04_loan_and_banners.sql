USE narainson_db;

CREATE TABLE IF NOT EXISTS dashboard_banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(500) NULL,
    image_url VARCHAR(1000) NOT NULL,
    action_type ENUM('none','url','screen') NOT NULL DEFAULT 'none',
    action_value VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    starts_at DATETIME NULL,
    ends_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_banners_active_sort (is_active, sort_order)
);

CREATE TABLE IF NOT EXISTS loan_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    application_code VARCHAR(32) NOT NULL UNIQUE,
    requested_amount DECIMAL(15,2) NOT NULL,
    tenure_months INT NOT NULL,
    purpose VARCHAR(80) NOT NULL,
    purpose_detail TEXT NULL,
    monthly_income DECIMAL(15,2) NULL,
    employment_type VARCHAR(80) NULL,
    company_name VARCHAR(200) NULL,
    bank_name VARCHAR(150) NULL,
    ifsc_code VARCHAR(15) NULL,
    bank_account_last4 CHAR(4) NULL,
    status ENUM('pending','approved','rejected','disbursed','cancelled') NOT NULL DEFAULT 'pending',
    status_message VARCHAR(500) NOT NULL DEFAULT 'Aapka loan application pending hai. Admin jald approve karega.',
    admin_note TEXT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_loan_user_status (user_id, status),
    INDEX idx_loan_created (created_at)
);

-- Seed banners only into an empty table, so re-running never brings back banners an admin deleted.
INSERT INTO dashboard_banners (title, subtitle, image_url, action_type, action_value, sort_order, is_active)
SELECT * FROM (
    SELECT 'Instant Loan upto ₹50,000' AS title, 'Apply in minutes — admin approval shortly' AS subtitle, 'https://cdn.example.com/banners/loan-hero.png' AS image_url, 'screen' AS action_type, 'loan_apply' AS action_value, 1 AS sort_order, 1 AS is_active
    UNION ALL SELECT 'Complete KYC', 'Faster approval when profile is complete', 'https://cdn.example.com/banners/kyc.png', 'screen', 'onboarding', 2, 1
    UNION ALL SELECT 'Refer & Earn', 'Invite friends to Narainsons', 'https://cdn.example.com/banners/refer.png', 'none', NULL, 3, 1
) seed
WHERE NOT EXISTS (SELECT 1 FROM dashboard_banners);

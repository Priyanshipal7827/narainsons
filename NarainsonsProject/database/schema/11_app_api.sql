USE narainson_db;

-- JWT: bumping token_version invalidates every access token already issued to that account.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Only the SHA-256 hash of a refresh token is stored. family_id links rotated tokens for reuse detection.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    family_id CHAR(36) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_user (user_id),
    INDEX idx_refresh_family (family_id)
);

-- One user can be signed in on several phones; push goes to all of them.
CREATE TABLE IF NOT EXISTS user_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fcm_token VARCHAR(512) NOT NULL,
    fcm_token_hash CHAR(64) NOT NULL UNIQUE,
    platform ENUM('android','ios','web') NOT NULL DEFAULT 'android',
    device_id VARCHAR(255) NULL,
    app_version VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_device_user (user_id)
);

-- Files uploaded by app users (payment proof, KYC). Served only to the owner and admins.
CREATE TABLE IF NOT EXISTS uploaded_files (
    id CHAR(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose ENUM('payment_proof','profile_image','aadhaar','pan') NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_file_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_file_user (user_id)
);

ALTER TABLE loan_applications ADD COLUMN interest_rate_annual DECIMAL(5,2) NOT NULL DEFAULT 0;
ALTER TABLE loan_applications ADD COLUMN processing_fee DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE loan_applications ADD COLUMN total_payable DECIMAL(15,2) NULL;
ALTER TABLE loan_applications ADD COLUMN emi_amount DECIMAL(15,2) NULL;
ALTER TABLE loan_applications ADD COLUMN disbursed_at DATETIME NULL;
ALTER TABLE loan_applications ADD COLUMN disbursement_reference VARCHAR(100) NULL;
ALTER TABLE loan_applications ADD COLUMN closed_at DATETIME NULL;
ALTER TABLE loan_applications MODIFY COLUMN status ENUM('pending','approved','rejected','disbursed','cancelled','closed') NOT NULL DEFAULT 'pending';

ALTER TABLE user_notifications MODIFY COLUMN category ENUM('loan_status','emi_reminder','emi_payment','kyc','general') NOT NULL;

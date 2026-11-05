USE narainson_db;

CREATE TABLE IF NOT EXISTS admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(20) NULL,
    role ENUM('super_admin','admin') NOT NULL DEFAULT 'admin',
    account_status ENUM('active','inactive') NOT NULL DEFAULT 'active',
    session_token VARCHAR(255) NULL,
    otp_hash VARCHAR(255) NULL,
    otp_expires_at DATETIME NULL,
    last_login DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    INDEX idx_admin_session (session_token),
    INDEX idx_admin_status (account_status)
);

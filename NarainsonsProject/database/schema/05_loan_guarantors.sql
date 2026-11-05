USE narainson_db;

CREATE TABLE IF NOT EXISTS loan_guarantors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_application_id BIGINT NOT NULL,
    guarantor_index TINYINT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(20) NOT NULL,
    relation VARCHAR(80) NOT NULL,
    address TEXT NULL,
    aadhaar_last4 CHAR(4) NULL,
    pan_last4 VARCHAR(4) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_guarantor_loan FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id) ON DELETE CASCADE,
    UNIQUE KEY uk_loan_guarantor_index (loan_application_id, guarantor_index),
    INDEX idx_guarantor_loan (loan_application_id)
);

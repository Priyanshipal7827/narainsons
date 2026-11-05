USE narainson_db;

ALTER TABLE users ADD COLUMN onboarding_step TINYINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN session_token VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN contacts_data JSON NULL;
ALTER TABLE users ADD COLUMN gallery_data JSON NULL;
ALTER TABLE users ADD INDEX idx_session_token (session_token);

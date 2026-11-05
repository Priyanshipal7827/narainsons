USE narainson_db;

-- Wrong-OTP counter, login lockout and session expiry.
-- Existing sessions have no expiry, so everyone signs in once more after this runs.
ALTER TABLE users ADD COLUMN otp_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN session_expires_at DATETIME NULL;

ALTER TABLE admins ADD COLUMN otp_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN locked_until DATETIME NULL;
ALTER TABLE admins ADD COLUMN session_expires_at DATETIME NULL;

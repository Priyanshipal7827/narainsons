USE narainson_db;

CREATE TABLE IF NOT EXISTS loan_apply_guide_video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    video_url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000) NULL,
    video_type ENUM('mp4','youtube','vimeo','hls') NOT NULL DEFAULT 'mp4',
    duration_seconds INT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'hi',
    sort_order INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_guide_video_active (is_active, sort_order)
);

INSERT INTO loan_apply_guide_video (title, description, video_url, thumbnail_url, video_type, duration_seconds, sort_order, is_active)
SELECT
    'Loan kaise apply karein',
    'Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.',
    'https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4',
    'https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg',
    'mp4',
    180,
    1,
    1
WHERE NOT EXISTS (SELECT 1 FROM loan_apply_guide_video WHERE is_active = 1 LIMIT 1);

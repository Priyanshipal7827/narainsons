USE narainson_db;

ALTER TABLE admins
  ADD COLUMN profile_image_path VARCHAR(255) NULL AFTER mobile_number;

-- Existing OTPs are short-lived and cannot be safely converted to a peppered hash.
DELETE FROM otp_codes;

ALTER TABLE otp_codes RENAME COLUMN code TO code_hash;
ALTER TABLE otp_codes ALTER COLUMN code_hash TYPE VARCHAR(64);
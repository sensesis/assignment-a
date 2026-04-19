ALTER TABLE enrollments ADD COLUMN expires_at TIMESTAMP(6);

CREATE INDEX idx_enrollments_pending_expires
    ON enrollments(expires_at)
    WHERE status = 'PENDING';

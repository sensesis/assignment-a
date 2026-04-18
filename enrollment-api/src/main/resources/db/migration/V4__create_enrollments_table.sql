CREATE TABLE enrollments (
    enrollment_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id       BIGINT NOT NULL REFERENCES classes(class_id),
    user_id        BIGINT NOT NULL REFERENCES users(user_id),
    status         VARCHAR(20) NOT NULL,
    enrolled_at    TIMESTAMP(6) NOT NULL,
    confirmed_at   TIMESTAMP(6),
    cancelled_at   TIMESTAMP(6),
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_enrollment_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_enrollments_user_id ON enrollments(user_id);
CREATE INDEX idx_enrollments_class_id ON enrollments(class_id);
CREATE UNIQUE INDEX uk_active_enrollment ON enrollments(class_id, user_id)
    WHERE status IN ('PENDING', 'CONFIRMED');

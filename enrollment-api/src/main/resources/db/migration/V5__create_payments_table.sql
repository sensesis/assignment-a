CREATE TABLE payments (
    payment_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id  BIGINT NOT NULL REFERENCES enrollments(enrollment_id),
    amount         INTEGER NOT NULL,
    status         VARCHAR(20) NOT NULL,
    paid_at        TIMESTAMP(6) NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_status CHECK (status IN ('PAID', 'REFUNDED', 'FAILED'))
);

CREATE INDEX idx_payments_enrollment_id ON payments(enrollment_id);
CREATE UNIQUE INDEX uk_payment_paid ON payments(enrollment_id) WHERE status = 'PAID';

CREATE TABLE waitlists (
    waitlist_id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    class_id                BIGINT NOT NULL REFERENCES classes(class_id),
    user_id                 BIGINT NOT NULL REFERENCES users(user_id),
    status                  VARCHAR(20) NOT NULL,
    promoted_enrollment_id  BIGINT REFERENCES enrollments(enrollment_id),
    promoted_at             TIMESTAMP(6),
    cancelled_at            TIMESTAMP(6),
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_waitlist_status CHECK (status IN ('WAITING', 'PROMOTED', 'CANCELLED')),
    CONSTRAINT chk_waitlist_promotion CHECK (
        (status = 'PROMOTED' AND promoted_enrollment_id IS NOT NULL AND promoted_at IS NOT NULL)
        OR (status <> 'PROMOTED')
    ),
    CONSTRAINT chk_waitlist_cancelled CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    )
);

CREATE INDEX idx_waitlists_class_id ON waitlists(class_id);
CREATE INDEX idx_waitlists_user_id ON waitlists(user_id);

-- 한 유저는 한 강의에 1 WAITING만 (CANCELLED 후 재대기 허용)
CREATE UNIQUE INDEX uk_active_waitlist ON waitlists(class_id, user_id)
    WHERE status = 'WAITING';

-- FIFO 승격 + 동일 시각 타이브레이커
CREATE INDEX idx_waitlist_fifo ON waitlists(class_id, created_at, waitlist_id)
    WHERE status = 'WAITING';

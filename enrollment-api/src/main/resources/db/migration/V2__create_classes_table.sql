CREATE TABLE classes (
    class_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_by     BIGINT NOT NULL REFERENCES users(user_id),
    title          VARCHAR(100) NOT NULL,
    description    TEXT,
    price          INTEGER NOT NULL,
    capacity       INTEGER NOT NULL,
    enrolled_count INTEGER NOT NULL DEFAULT 0,
    start_date     DATE NOT NULL,
    end_date       DATE NOT NULL,
    status         VARCHAR(20) NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_capacity CHECK (capacity > 0),
    CONSTRAINT chk_enrolled CHECK (enrolled_count >= 0 AND enrolled_count <= capacity),
    CONSTRAINT chk_price CHECK (price >= 0),
    CONSTRAINT chk_date_range CHECK (end_date > start_date)
);

CREATE INDEX idx_classes_status ON classes(status);
CREATE INDEX idx_classes_created_by ON classes(created_by);
CREATE INDEX idx_classes_created_by_status ON classes(created_by, status);

package com.enrollment.domain.classes.entity;

public enum ClassStatus {
    DRAFT, OPEN, CLOSED;

    // 상태 전환 가능 여부 검증
    public boolean canTransitionTo(ClassStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}

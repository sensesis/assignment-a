package com.enrollment.domain.waitlist.entity;

public enum WaitlistStatus {
    WAITING, PROMOTED, CANCELLED;

    // 상태 전환 가능 여부 검증
    public boolean canTransitionTo(WaitlistStatus target) {
        return switch (this) {
            case WAITING -> target == PROMOTED || target == CANCELLED;
            case PROMOTED -> false;
            case CANCELLED -> false;
        };
    }
}

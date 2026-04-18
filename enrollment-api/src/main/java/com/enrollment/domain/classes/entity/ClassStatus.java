package com.enrollment.domain.classes.entity;

public enum ClassStatus {
    DRAFT, OPEN, CLOSED;

    public boolean canTransitionTo(ClassStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}

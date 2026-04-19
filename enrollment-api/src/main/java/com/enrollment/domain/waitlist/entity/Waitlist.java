package com.enrollment.domain.waitlist.entity;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.user.entity.User;
import com.enrollment.global.entity.BaseEntity;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlists")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Waitlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status;

    @Column(name = "promoted_enrollment_id")
    private Long promotedEnrollmentId;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // 대기열 승격
    public void promote(Long enrollmentId) {
        validateTransition(WaitlistStatus.PROMOTED);
        this.status = WaitlistStatus.PROMOTED;
        this.promotedEnrollmentId = enrollmentId;
        this.promotedAt = LocalDateTime.now();
    }

    // 대기열 철회
    public void cancel() {
        if (this.status == WaitlistStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED);
        }
        validateTransition(WaitlistStatus.CANCELLED);
        this.status = WaitlistStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    // 선두 스킵 — 승격 시 이미 활성 수강 신청을 가진 대기자를 건너뛰기 위한 방어 처리.
    // 호출부(WaitlistService.promoteNext)가 WAITING 상태만 넘겨주는 것을 보장하므로 상태머신 검증 생략.
    public void skip() {
        this.status = WaitlistStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    // 대기열 소유자 검증
    public void validateOwner(Long userId) {
        if (this.user == null || !this.user.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_WAITLIST_OWNER);
        }
    }

    // 상태 전환 검증
    private void validateTransition(WaitlistStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}

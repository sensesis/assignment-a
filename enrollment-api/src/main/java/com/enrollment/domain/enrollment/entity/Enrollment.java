package com.enrollment.domain.enrollment.entity;

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
@Table(name = "enrollments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Enrollment extends BaseEntity {

    private static final int CANCEL_WINDOW_DAYS = 7;
    // 결제 가능 시간 30분: 카드 결제 평균 + 재로그인/카드 교체 여유.
    // 운영 중 조정 빈도가 낮다고 판단해 상수로 유지. 필요 시 application.yml로 이동.
    private static final int PENDING_TTL_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // 수강 신청 확정
    public void confirm() {
        if (isExpired()) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }
        validateTransition(EnrollmentStatus.CONFIRMED);
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.expiresAt = null;
    }

    // 수강 신청 취소
    public void cancel() {
        if (this.status == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELLED);
        }
        if (this.status == EnrollmentStatus.CONFIRMED) {
            if (this.confirmedAt == null
                    || this.confirmedAt.plusDays(CANCEL_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.CANCEL_PERIOD_EXPIRED);
            }
        }
        validateTransition(EnrollmentStatus.CANCELLED);
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    // PENDING TTL 만료 처리 (스케줄러 전용, 사용자 취소와 구분)
    // - cancel()과 달리 상태 전이 검증 생략 (호출부에서 PENDING + 만료 확정)
    // - 7일 기한 검증도 불필요 (만료 자체가 시스템 자동 종료)
    public void expire() {
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    // TTL 만료 여부 확인
    public boolean isExpired() {
        return this.expiresAt != null && this.expiresAt.isBefore(LocalDateTime.now());
    }

    // 신청 / 승격 시 공통 TTL 세팅
    public static LocalDateTime defaultExpiresAt() {
        return LocalDateTime.now().plusMinutes(PENDING_TTL_MINUTES);
    }

    // 수강 신청 소유자 검증
    public void validateOwner(Long userId) {
        if (this.user == null || !this.user.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER);
        }
    }

    // 상태 전환 검증
    private void validateTransition(EnrollmentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}

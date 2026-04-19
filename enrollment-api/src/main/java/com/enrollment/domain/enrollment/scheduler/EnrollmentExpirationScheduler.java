package com.enrollment.domain.enrollment.scheduler;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.waitlist.service.WaitlistService;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentExpirationScheduler {

    // 배치 크기 100 + 1분 주기 = 시간당 최대 6,000건 처리.
    // 이보다 많은 만료율이 예상되면 BATCH_SIZE 상향 또는 다중 tick drain 방식으로 전환.
    private static final int BATCH_SIZE = 100;

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final WaitlistService waitlistService;
    private final TransactionTemplate transactionTemplate;

    // 만료된 PENDING 스캔 (1분 주기)
    @Scheduled(fixedDelay = 60_000)  // 1분 주기 — DB 부하/응답 지연 균형
    public void expirePendings() {
        List<Enrollment> expired = enrollmentRepository.findExpiredPendings(
                EnrollmentStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        for (Enrollment e : expired) {
            try {
                transactionTemplate.executeWithoutResult(s -> expireOne(e.getId()));
            } catch (Exception ex) {
                // 한 건 실패가 전체 배치 중단을 막지 않음
                log.warn("[Expire] Failed to expire enrollment={}", e.getId(), ex);
            }
        }
    }

    // 단건 만료 처리 (독립 트랜잭션)
    public void expireOne(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElse(null);
        if (enrollment == null
                || enrollment.getStatus() != EnrollmentStatus.PENDING
                || !enrollment.isExpired()) {
            return;
        }
        ClassEntity classEntity = classRepository.findByIdForUpdate(enrollment.getClassEntity().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));

        // 만료 처리 + 좌석 복구
        enrollment.expire();
        classEntity.decrementEnrolled();

        log.info("[Expire] enrollment={} user={} class={} expired, seat released",
                enrollmentId, enrollment.getUser().getId(), classEntity.getId());

        // 체인 승격
        waitlistService.promoteNext(classEntity);
    }
}

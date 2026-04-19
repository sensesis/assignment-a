package com.enrollment.domain.waitlist.service;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.dto.WaitlistResponse;
import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
import com.enrollment.domain.waitlist.repository.WaitlistRepository;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    // 대기열 등록
    @Transactional
    public WaitlistResponse register(Long userId, Long classId) {

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 강의 조회 (읽기 전용이라 락 불필요, uk_active_waitlist 유니크 인덱스로 중복 대기 차단)
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));

        // 강의 상태 검증
        if (classEntity.getStatus() != ClassStatus.OPEN) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_ALLOWED);
        }

        // 정원 여유 검증
        if (classEntity.hasVacancy()) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_ALLOWED);
        }

        // 활성 수강 신청 존재 여부 검증
        if (enrollmentRepository.existsActiveByClassIdAndUserId(classId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        // 대기열 중복 검증
        if (waitlistRepository.existsByClassIdAndUserIdAndStatus(classId, userId, WaitlistStatus.WAITING)) {
            throw new BusinessException(ErrorCode.ALREADY_IN_WAITLIST);
        }

        // 대기열 저장
        return WaitlistResponse.from(waitlistRepository.save(
                Waitlist.builder()
                        .classEntity(classEntity)
                        .user(user)
                        .status(WaitlistStatus.WAITING)
                        .build()));
    }

    // 대기열 철회
    @Transactional
    public void cancelWaitlist(Long userId, Long classId) {

        // 대기열 조회
        Waitlist waitlist = waitlistRepository
                .findByClassIdAndUserIdAndStatus(classId, userId, WaitlistStatus.WAITING)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));

        // 대기열 소유자 검증
        waitlist.validateOwner(userId);

        // 대기열 취소
        waitlist.cancel();
    }

    // 선두 자동 승격 (EnrollmentService.cancel 내부에서 호출, 같은 트랜잭션)
    @Transactional
    public void promoteNext(ClassEntity classEntity) {
        while (true) {

            // 선두 대기열 조회
            Optional<Waitlist> found = waitlistRepository.findFirstWaitingForUpdate(classEntity.getId());
            if (found.isEmpty()) {
                return;
            }
            Waitlist next = found.get();

            // 선두가 이미 활성 수강 신청 보유 시 skip
            if (enrollmentRepository.existsActiveByClassIdAndUserId(
                    classEntity.getId(), next.getUser().getId())) {
                next.skip();
                continue;
            }

            // 좌석 재차지
            classEntity.incrementEnrolled();

            // PENDING 수강 신청 생성
            Enrollment promoted = enrollmentRepository.save(
                    Enrollment.builder()
                            .classEntity(classEntity)
                            .user(next.getUser())
                            .status(EnrollmentStatus.PENDING)
                            .enrolledAt(LocalDateTime.now())
                            .expiresAt(Enrollment.defaultExpiresAt())
                            .build());

            // 대기열 승격
            next.promote(promoted.getId());

            // 승격 알림 로그
            log.info("[Waitlist] Promoted user={} to enrollment={} (expiresAt={})",
                    next.getUser().getId(), promoted.getId(), promoted.getExpiresAt());
            break;
        }
    }
}

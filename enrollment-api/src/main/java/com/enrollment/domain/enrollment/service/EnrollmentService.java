package com.enrollment.domain.enrollment.service;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.dto.EnrollmentCreateRequest;
import com.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.enrollment.domain.enrollment.dto.EnrollmentWithUserResponse;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.payment.entity.Payment;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.payment.repository.PaymentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.service.WaitlistService;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;

    // 수강 신청
    @Transactional
    public EnrollmentResponse enroll(Long userId, EnrollmentCreateRequest request) {
        
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 강의 조회
        ClassEntity classEntity = classRepository.findByIdForUpdate(request.classId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));

        // 강의 상태 검증
        if (classEntity.getStatus() != ClassStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        // 수강 신청 존재 여부 검증
        if (enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                classEntity.getId(), user.getId(), ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        // 좌석 차감
        classEntity.incrementEnrolled();

        // 수강 신청 저장
        return EnrollmentResponse.from(enrollmentRepository.save(
                Enrollment.builder()
                        .classEntity(classEntity)
                        .user(user)
                        .status(EnrollmentStatus.PENDING)
                        .enrolledAt(LocalDateTime.now())
                        .expiresAt(Enrollment.defaultExpiresAt())
                        .build()));
    }

    // 결제
    @Transactional
    public EnrollmentResponse pay(Long userId, Long enrollmentId) {

        // 수강 신청 조회 (만료 스케줄러와의 race 방지 위해 비관적 락으로 직렬화)
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        // 수강 신청 소유자 검증
        enrollment.validateOwner(userId);

        // 수강 신청 확정
        enrollment.confirm();

        // 결제 저장
        paymentRepository.save(Payment.paid(enrollment, enrollment.getClassEntity().getPrice()));
        
        return EnrollmentResponse.from(enrollment);
    }

    // 수강 취소
    @Transactional
    public EnrollmentResponse cancel(Long userId, Long enrollmentId) {
        
        // 수강 신청 조회
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        // 수강 신청 소유자 검증
        enrollment.validateOwner(userId);

        // CONFIRMED 여부 확인
        boolean wasConfirmed = enrollment.getStatus() == EnrollmentStatus.CONFIRMED;

        // 강의 조회
        ClassEntity classEntity = classRepository.findByIdForUpdate(enrollment.getClassEntity().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));

        // 수강 신청 취소
        enrollment.cancel();

        // 강의 수강생 수 감소
        classEntity.decrementEnrolled();

        // 환불 이력 저장
        if (wasConfirmed) {
            paymentRepository.save(Payment.refunded(enrollment, classEntity.getPrice()));
        }

        // 대기열 선두 자동 승격
        waitlistService.promoteNext(classEntity);

        return EnrollmentResponse.from(enrollment);
    }

    // 내 수강 신청 목록 조회
    public Page<EnrollmentResponse> getMyEnrollments(Long userId, Pageable pageable) {
        
        // 사용자 존재 여부 검증
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return enrollmentRepository.findByUserId(userId, pageable)
                .map(EnrollmentResponse::from);
    }

    // 강의별 수강생 목록 조회 (강사 전용)
    public Page<EnrollmentWithUserResponse> getClassEnrollments(Long userId, Long classId, Pageable pageable) {
        
        // 강의 조회
        ClassEntity classEntity = classRepository.findWithCreatorById(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));

        // 강의 소유자 검증
        classEntity.validateOwner(userId);

        return enrollmentRepository.findByClassEntityId(classId, pageable)
                .map(EnrollmentWithUserResponse::from);
    }
}

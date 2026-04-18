package com.enrollment.domain.enrollment.service;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.dto.EnrollmentCreateRequest;
import com.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

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
                        .build()));
    }
}

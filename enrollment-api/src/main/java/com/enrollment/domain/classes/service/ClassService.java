package com.enrollment.domain.classes.service;

import com.enrollment.domain.classes.dto.ClassCreateRequest;
import com.enrollment.domain.classes.dto.ClassResponse;
import com.enrollment.domain.classes.dto.ClassUpdateRequest;
import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassService {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    // 강의 등록
    @Transactional
    public ClassResponse create(Long userId, ClassCreateRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && !request.endDate().isAfter(request.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != UserRole.CREATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE);
        }

        ClassEntity entity = ClassEntity.builder()
                .createdBy(user)
                .title(request.title())
                .description(request.description())
                .price(request.price())
                .capacity(request.capacity())
                .enrolledCount(0)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(ClassStatus.DRAFT)
                .build();

        ClassEntity saved = classRepository.save(entity);
        return ClassResponse.from(saved);
    }

    // 강의 수정
    @Transactional
    public ClassResponse update(Long userId, Long classId, ClassUpdateRequest request) {
        ClassEntity entity = classRepository.findWithCreatorById(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        entity.validateOwner(userId);
        entity.update(request.title(), request.description(), request.price(),
                request.capacity(), request.startDate(), request.endDate());
        return ClassResponse.from(entity);
    }

    // 강의 공개
    @Transactional
    public ClassResponse publish(Long userId, Long classId) {
        ClassEntity entity = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        entity.validateOwner(userId);
        entity.publish();
        return ClassResponse.from(entity);
    }

    // 모집 마감
    @Transactional
    public ClassResponse close(Long userId, Long classId) {
        ClassEntity entity = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLASS_NOT_FOUND));
        entity.validateOwner(userId);
        entity.close();
        return ClassResponse.from(entity);
    }
}

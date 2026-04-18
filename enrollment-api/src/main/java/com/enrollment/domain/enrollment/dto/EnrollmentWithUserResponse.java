package com.enrollment.domain.enrollment.dto;

import com.enrollment.domain.enrollment.entity.Enrollment;

import java.time.LocalDateTime;

public record EnrollmentWithUserResponse(
        Long enrollmentId,
        Long userId,
        String userName,
        String status,
        LocalDateTime enrolledAt
) {

    public static EnrollmentWithUserResponse from(Enrollment enrollment) {
        return new EnrollmentWithUserResponse(
                enrollment.getId(),
                enrollment.getUser().getId(),
                enrollment.getUser().getName(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt()
        );
    }
}

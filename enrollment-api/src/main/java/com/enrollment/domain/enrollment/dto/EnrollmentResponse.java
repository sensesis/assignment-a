package com.enrollment.domain.enrollment.dto;

import com.enrollment.domain.enrollment.entity.Enrollment;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long enrollmentId,
        Long classId,
        String classTitle,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {

    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getClassEntity().getId(),
                enrollment.getClassEntity().getTitle(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt()
        );
    }
}

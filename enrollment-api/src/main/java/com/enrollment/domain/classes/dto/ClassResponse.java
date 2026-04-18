package com.enrollment.domain.classes.dto;

import com.enrollment.domain.classes.entity.ClassEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClassResponse(
        Long classId,
        String title,
        String description,
        Integer price,
        Integer capacity,
        Integer enrolledCount,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String creatorName,
        LocalDateTime createdAt
) {

    public static ClassResponse from(ClassEntity entity) {
        return new ClassResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getCapacity(),
                entity.getEnrolledCount(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus().name(),
                entity.getCreatedBy().getName(),
                entity.getCreatedAt()
        );
    }
}

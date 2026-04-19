package com.enrollment.domain.classes.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClassUpdateRequest(
        @Size(max = 100) String title,
        @Size(max = 2000) String description,
        Integer price,
        Integer capacity,
        LocalDate startDate,
        LocalDate endDate
) {
}

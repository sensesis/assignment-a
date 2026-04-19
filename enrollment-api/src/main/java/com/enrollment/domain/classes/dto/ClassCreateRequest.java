package com.enrollment.domain.classes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClassCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 2000) String description,
        @NotNull @PositiveOrZero Integer price,
        @NotNull @Positive Integer capacity,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}

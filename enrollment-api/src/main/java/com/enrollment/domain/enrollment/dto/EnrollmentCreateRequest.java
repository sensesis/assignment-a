package com.enrollment.domain.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollmentCreateRequest(
        @NotNull Long classId
) {
}

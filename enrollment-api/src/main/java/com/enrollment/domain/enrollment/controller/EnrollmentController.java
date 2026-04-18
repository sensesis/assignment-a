package com.enrollment.domain.enrollment.controller;

import com.enrollment.domain.enrollment.dto.EnrollmentCreateRequest;
import com.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.enrollment.domain.enrollment.service.EnrollmentService;
import com.enrollment.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollment API", description = "수강 신청 관리 API")
@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "수강 신청", description = "강의를 수강 신청합니다. 정원/중복 체크 후 PENDING 상태로 생성됩니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "수강 신청 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = EnrollmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 입력값 또는 상태 (INVALID_INPUT, INVALID_STATE_TRANSITION, CAPACITY_EXCEEDED)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "사용자 또는 강의를 찾을 수 없음 (USER_NOT_FOUND, CLASS_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 신청한 강의 (ALREADY_ENROLLED)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid EnrollmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(userId, request));
    }

    @Operation(summary = "결제", description = "수강 신청을 결제하여 CONFIRMED 상태로 전환합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "결제 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = EnrollmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "이미 확정/취소된 신청 또는 잘못된 상태 전환 (INVALID_STATE_TRANSITION)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "수강 신청 소유자가 아님 (NOT_ENROLLMENT_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "수강 신청을 찾을 수 없음 (ENROLLMENT_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/pay")
    public ResponseEntity<EnrollmentResponse> pay(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(enrollmentService.pay(userId, id));
    }

    @Operation(summary = "수강 취소", description = "수강 신청을 취소합니다. CONFIRMED 상태라면 7일 이내에만 가능하며 환불 처리됩니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "취소 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = EnrollmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "취소 가능 기간 초과 또는 이미 취소됨 (CANCEL_PERIOD_EXPIRED, ALREADY_CANCELLED)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "수강 신청 소유자가 아님 (NOT_ENROLLMENT_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "수강 신청을 찾을 수 없음 (ENROLLMENT_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<EnrollmentResponse> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(enrollmentService.cancel(userId, id));
    }
}

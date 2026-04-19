package com.enrollment.domain.waitlist.controller;

import com.enrollment.domain.waitlist.dto.WaitlistResponse;
import com.enrollment.domain.waitlist.service.WaitlistService;
import com.enrollment.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Waitlist API", description = "대기열 관리 API")
@RestController
@RequestMapping("/classes/{classId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "대기열 등록", description = "정원이 찬 강의에 대기열로 등록합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "대기열 등록 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = WaitlistResponse.class))),
        @ApiResponse(responseCode = "400", description = "대기열 등록 불가 상태 (WAITLIST_NOT_ALLOWED, INVALID_INPUT)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "사용자 또는 강의를 찾을 수 없음 (USER_NOT_FOUND, CLASS_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 신청 또는 대기 중 (ALREADY_ENROLLED, ALREADY_IN_WAITLIST)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<WaitlistResponse> register(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long classId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.register(userId, classId));
    }

    @Operation(summary = "대기열 철회", description = "본인이 등록한 대기열을 철회합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "대기열 철회 성공"),
        @ApiResponse(responseCode = "403", description = "대기열 소유자가 아님 (NOT_WAITLIST_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "대기열 정보를 찾을 수 없음 (WAITLIST_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long classId) {
        waitlistService.cancelWaitlist(userId, classId);
        return ResponseEntity.noContent().build();
    }
}

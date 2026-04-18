package com.enrollment.domain.classes.controller;

import com.enrollment.domain.classes.dto.ClassCreateRequest;
import com.enrollment.domain.classes.dto.ClassResponse;
import com.enrollment.domain.classes.dto.ClassUpdateRequest;
import com.enrollment.domain.classes.service.ClassService;
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

@Tag(name = "Class API", description = "강의 관리 API")
@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @Operation(summary = "강의 등록", description = "새로운 강의를 등록합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "강의 등록 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ClassResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 입력값 (INVALID_INPUT)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "강사 역할이 아님 (FORBIDDEN_ROLE)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (USER_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ClassResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid ClassCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(classService.create(userId, request));
    }

    @Operation(summary = "강의 수정", description = "기존 강의 정보를 수정합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "강의 수정 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ClassResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 상태 전환 또는 입력값 (INVALID_STATE_TRANSITION, INVALID_INPUT)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님 (NOT_COURSE_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음 (CLASS_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ClassResponse> update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @RequestBody @Valid ClassUpdateRequest request) {
        return ResponseEntity.ok(classService.update(userId, id, request));
    }

    @Operation(summary = "강의 공개", description = "강의를 공개 상태로 전환합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "강의 공개 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ClassResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 상태 전환 또는 입력값 (INVALID_STATE_TRANSITION, INVALID_INPUT)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님 (NOT_COURSE_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음 (CLASS_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ClassResponse> publish(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(classService.publish(userId, id));
    }

    @Operation(summary = "모집 마감", description = "강의 수강 신청 모집을 마감합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "모집 마감 성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ClassResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 상태 전환 (INVALID_STATE_TRANSITION)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "강의 소유자가 아님 (NOT_COURSE_OWNER)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "강의를 찾을 수 없음 (CLASS_NOT_FOUND)",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/close")
    public ResponseEntity<ClassResponse> close(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(classService.close(userId, id));
    }
}

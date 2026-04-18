package com.enrollment.global.error.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다"),
    CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "CAPACITY_EXCEEDED", "정원이 초과되었습니다"),
    CANCEL_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "CANCEL_PERIOD_EXPIRED", "취소 가능 기간(7일)을 초과했습니다"),
    ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "ALREADY_CANCELLED", "이미 취소된 수강 신청입니다"),
    INVALID_STATE_TRANSITION(HttpStatus.BAD_REQUEST, "INVALID_STATE_TRANSITION", "허용되지 않은 상태 전이입니다"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    NOT_COURSE_OWNER(HttpStatus.FORBIDDEN, "NOT_COURSE_OWNER", "해당 강의의 소유자가 아닙니다"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "해당 역할은 이 작업을 수행할 수 없습니다"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "강의를 찾을 수 없습니다"),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "수강 신청 내역을 찾을 수 없습니다"),

    ALREADY_ENROLLED(HttpStatus.CONFLICT, "ALREADY_ENROLLED", "이미 신청한 강의입니다"),

    LOCK_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "LOCK_TIMEOUT", "요청이 혼잡합니다. 잠시 후 다시 시도해주세요"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

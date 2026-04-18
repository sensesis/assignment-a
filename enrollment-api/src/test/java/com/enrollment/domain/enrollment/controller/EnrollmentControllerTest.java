package com.enrollment.domain.enrollment.controller;

import com.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.enrollment.domain.enrollment.service.EnrollmentService;
import com.enrollment.global.error.GlobalExceptionHandler;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnrollmentController.class)
@Import(GlobalExceptionHandler.class)
class EnrollmentControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    EnrollmentService enrollmentService;

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 0, 0);

    private EnrollmentResponse sampleResponse(String status) {
        return new EnrollmentResponse(100L, 10L, "Java 기초", status, NOW, null, null);
    }

    @Nested
    class Enroll {

        @Test
        void 정상_신청_201() throws Exception {
            given(enrollmentService.enroll(eq(2L), any())).willReturn(sampleResponse("PENDING"));

            String body = """
                    {"classId": 10}
                    """;

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enrollmentId").value(100))
                    .andExpect(jsonPath("$.classId").value(10))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void classId_누락_시_400() throws Exception {
            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 강의_미존재_시_404() throws Exception {
            given(enrollmentService.enroll(eq(2L), any()))
                    .willThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classId\": 999}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }

        @Test
        void 사용자_미존재_시_404() throws Exception {
            given(enrollmentService.enroll(eq(999L), any()))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classId\": 10}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        void 정원_초과_시_400() throws Exception {
            given(enrollmentService.enroll(eq(2L), any()))
                    .willThrow(new BusinessException(ErrorCode.CAPACITY_EXCEEDED));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classId\": 10}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
        }

        @Test
        void 이미_신청한_강의면_409() throws Exception {
            given(enrollmentService.enroll(eq(2L), any()))
                    .willThrow(new BusinessException(ErrorCode.ALREADY_ENROLLED));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classId\": 10}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        }
    }

    @Nested
    class Pay {

        @Test
        void 정상_결제_200() throws Exception {
            given(enrollmentService.pay(2L, 100L)).willReturn(sampleResponse("CONFIRMED"));

            mockMvc.perform(patch("/enrollments/100/pay")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void 신청_미존재_시_404() throws Exception {
            given(enrollmentService.pay(2L, 999L))
                    .willThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

            mockMvc.perform(patch("/enrollments/999/pay")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            given(enrollmentService.pay(999L, 100L))
                    .willThrow(new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER));

            mockMvc.perform(patch("/enrollments/100/pay")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ENROLLMENT_OWNER"));
        }
    }

    @Nested
    class Cancel {

        @Test
        void 정상_취소_200() throws Exception {
            given(enrollmentService.cancel(2L, 100L)).willReturn(sampleResponse("CANCELLED"));

            mockMvc.perform(patch("/enrollments/100/cancel")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void 취소_기간_초과_시_400() throws Exception {
            given(enrollmentService.cancel(2L, 100L))
                    .willThrow(new BusinessException(ErrorCode.CANCEL_PERIOD_EXPIRED));

            mockMvc.perform(patch("/enrollments/100/cancel")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANCEL_PERIOD_EXPIRED"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            given(enrollmentService.cancel(999L, 100L))
                    .willThrow(new BusinessException(ErrorCode.NOT_ENROLLMENT_OWNER));

            mockMvc.perform(patch("/enrollments/100/cancel")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_ENROLLMENT_OWNER"));
        }

        @Test
        void 이미_취소된_신청이면_400() throws Exception {
            given(enrollmentService.cancel(2L, 100L))
                    .willThrow(new BusinessException(ErrorCode.ALREADY_CANCELLED));

            mockMvc.perform(patch("/enrollments/100/cancel")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        }
    }

    @Nested
    class GetMyEnrollments {

        @Test
        void 내_신청_목록_200() throws Exception {
            given(enrollmentService.getMyEnrollments(eq(2L), any()))
                    .willReturn(new PageImpl<>(List.of(sampleResponse("PENDING")),
                            PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/enrollments/me")
                            .header("X-User-Id", 2L)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].enrollmentId").value(100))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void 사용자_미존재_시_404() throws Exception {
            given(enrollmentService.getMyEnrollments(eq(999L), any()))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/enrollments/me")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }
}

package com.enrollment.domain.classes.controller;

import com.enrollment.domain.classes.dto.ClassResponse;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.service.ClassService;
import com.enrollment.global.error.GlobalExceptionHandler;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClassController.class)
@Import(GlobalExceptionHandler.class)
class ClassControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    ClassService classService;

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 0, 0);

    private ClassResponse sampleResponse() {
        return new ClassResponse(1L, "Java 기초", "설명", 10000, 30, 0,
                LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                "DRAFT", "instructor", NOW);
    }

    @Nested
    class CreateClass {

        @Test
        void 정상_생성_201() throws Exception {
            given(classService.create(eq(1L), any())).willReturn(sampleResponse());

            String body = """
                    {
                        "title": "Java 기초",
                        "description": "설명",
                        "price": 10000,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.classId").value(1))
                    .andExpect(jsonPath("$.title").value("Java 기초"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        void CLASSMATE_등록_시_403() throws Exception {
            given(classService.create(eq(2L), any()))
                    .willThrow(new BusinessException(ErrorCode.FORBIDDEN_ROLE));

            String body = """
                    {
                        "title": "Java 기초",
                        "price": 10000,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 2L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
        }

        @Test
        void title_빈문자열_시_400() throws Exception {
            String body = """
                    {
                        "title": "",
                        "price": 10000,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void price_음수_시_400() throws Exception {
            String body = """
                    {
                        "title": "Java",
                        "price": -1,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void capacity_0_시_400() throws Exception {
            String body = """
                    {
                        "title": "Java",
                        "price": 10000,
                        "capacity": 0,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void title_누락_시_400() throws Exception {
            String body = """
                    {
                        "price": 10000,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void X_User_Id_헤더_누락_시_400() throws Exception {
            String body = """
                    {
                        "title": "Java 기초",
                        "price": 10000,
                        "capacity": 30,
                        "startDate": "2025-03-01",
                        "endDate": "2025-06-30"
                    }
                    """;

            mockMvc.perform(post("/classes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    class UpdateClass {

        @Test
        void 정상_수정_200() throws Exception {
            ClassResponse updated = new ClassResponse(1L, "새 제목", "설명", 10000, 30, 0,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                    "DRAFT", "instructor", NOW);
            given(classService.update(eq(1L), eq(1L), any())).willReturn(updated);

            String body = """
                    {"title": "새 제목"}
                    """;

            mockMvc.perform(patch("/classes/1")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("새 제목"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            given(classService.update(eq(999L), eq(1L), any()))
                    .willThrow(new BusinessException(ErrorCode.NOT_COURSE_OWNER));

            String body = """
                    {"title": "새 제목"}
                    """;

            mockMvc.perform(patch("/classes/1")
                            .header("X-User-Id", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_COURSE_OWNER"));
        }

        @Test
        void OPEN_상태에서_update_시_400() throws Exception {
            given(classService.update(eq(1L), eq(1L), any()))
                    .willThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION));

            String body = """
                    {"title": "새 제목"}
                    """;

            mockMvc.perform(patch("/classes/1")
                            .header("X-User-Id", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }
    }

    @Nested
    class PublishClass {

        @Test
        void 정상_publish_200() throws Exception {
            ClassResponse published = new ClassResponse(1L, "Java 기초", "설명", 10000, 30, 0,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                    "OPEN", "instructor", NOW);
            given(classService.publish(1L, 1L)).willReturn(published);

            mockMvc.perform(patch("/classes/1/publish")
                            .header("X-User-Id", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        void 강의_미존재_시_404() throws Exception {
            given(classService.publish(1L, 999L))
                    .willThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND));

            mockMvc.perform(patch("/classes/999/publish")
                            .header("X-User-Id", 1L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }

        @Test
        void OPEN_상태에서_publish_시_400() throws Exception {
            given(classService.publish(1L, 1L))
                    .willThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION));

            mockMvc.perform(patch("/classes/1/publish")
                            .header("X-User-Id", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            given(classService.publish(999L, 1L))
                    .willThrow(new BusinessException(ErrorCode.NOT_COURSE_OWNER));

            mockMvc.perform(patch("/classes/1/publish")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_COURSE_OWNER"));
        }
    }

    @Nested
    class CloseClass {

        @Test
        void 정상_close_200() throws Exception {
            ClassResponse closed = new ClassResponse(1L, "Java 기초", "설명", 10000, 30, 0,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                    "CLOSED", "instructor", NOW);
            given(classService.close(1L, 1L)).willReturn(closed);

            mockMvc.perform(patch("/classes/1/close")
                            .header("X-User-Id", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        void DRAFT_상태에서_close_시_400() throws Exception {
            given(classService.close(1L, 1L))
                    .willThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION));

            mockMvc.perform(patch("/classes/1/close")
                            .header("X-User-Id", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            given(classService.close(999L, 1L))
                    .willThrow(new BusinessException(ErrorCode.NOT_COURSE_OWNER));

            mockMvc.perform(patch("/classes/1/close")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_COURSE_OWNER"));
        }
    }

    @Nested
    class GetClasses {

        @Test
        void status_필터_목록_조회_200() throws Exception {
            ClassResponse response = new ClassResponse(1L, "Java 기초", "설명", 10000, 30, 0,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                    "OPEN", "instructor", NOW);
            given(classService.getClasses(eq(ClassStatus.OPEN), any()))
                    .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/classes")
                            .param("status", "OPEN")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void 잘못된_sort_필드_400_INVALID_INPUT() throws Exception {
            given(classService.getClasses(eq(ClassStatus.OPEN), any()))
                    .willThrow(new org.springframework.data.mapping.PropertyReferenceException(
                            "nonexistent",
                            org.springframework.data.util.TypeInformation.of(Object.class),
                            java.util.Collections.emptyList()));

            mockMvc.perform(get("/classes")
                            .param("status", "OPEN")
                            .param("sort", "nonexistent"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void status_누락_시_default_OPEN() throws Exception {
            ClassResponse response = new ClassResponse(1L, "Java 기초", "설명", 10000, 30, 0,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30),
                    "OPEN", "instructor", NOW);
            given(classService.getClasses(eq(ClassStatus.OPEN), any()))
                    .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/classes")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"));
        }
    }

    @Nested
    class GetClassById {

        @Test
        void 단건_조회_200() throws Exception {
            given(classService.getClass(1L)).willReturn(sampleResponse());

            mockMvc.perform(get("/classes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.classId").value(1))
                    .andExpect(jsonPath("$.title").value("Java 기초"));
        }

        @Test
        void 미존재_시_404() throws Exception {
            given(classService.getClass(999L))
                    .willThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND));

            mockMvc.perform(get("/classes/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }
    }

    @Nested
    class GetMyClasses {

        @Test
        void 내_강의_목록_200() throws Exception {
            given(classService.getMyClasses(eq(1L), any()))
                    .willReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/classes/me")
                            .header("X-User-Id", 1L)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].classId").value(1));
        }

        @Test
        void 사용자_미존재_시_404() throws Exception {
            given(classService.getMyClasses(eq(999L), any()))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(get("/classes/me")
                            .header("X-User-Id", 999L)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }
}

package com.enrollment.domain.waitlist.controller;

import com.enrollment.domain.waitlist.dto.WaitlistResponse;
import com.enrollment.domain.waitlist.service.WaitlistService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitlistController.class)
@Import(GlobalExceptionHandler.class)
class WaitlistControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    WaitlistService waitlistService;

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 0, 0);

    private WaitlistResponse sampleResponse() {
        return new WaitlistResponse(500L, 10L, "Java 기초", "WAITING", NOW);
    }

    @Nested
    class Register {

        @Test
        void 정상_등록_201() throws Exception {
            given(waitlistService.register(2L, 10L)).willReturn(sampleResponse());

            mockMvc.perform(post("/classes/10/waitlist")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.waitlistId").value(500))
                    .andExpect(jsonPath("$.classId").value(10))
                    .andExpect(jsonPath("$.classTitle").value("Java 기초"))
                    .andExpect(jsonPath("$.status").value("WAITING"));
        }

        @Test
        void WAITLIST_NOT_ALLOWED_시_400() throws Exception {
            given(waitlistService.register(2L, 10L))
                    .willThrow(new BusinessException(ErrorCode.WAITLIST_NOT_ALLOWED));

            mockMvc.perform(post("/classes/10/waitlist")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WAITLIST_NOT_ALLOWED"));
        }

        @Test
        void 강의_미존재_시_404() throws Exception {
            given(waitlistService.register(2L, 999L))
                    .willThrow(new BusinessException(ErrorCode.CLASS_NOT_FOUND));

            mockMvc.perform(post("/classes/999/waitlist")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }

        @Test
        void 사용자_미존재_시_404() throws Exception {
            given(waitlistService.register(999L, 10L))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/classes/10/waitlist")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        void 이미_신청한_강의면_409() throws Exception {
            given(waitlistService.register(2L, 10L))
                    .willThrow(new BusinessException(ErrorCode.ALREADY_ENROLLED));

            mockMvc.perform(post("/classes/10/waitlist")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        }

        @Test
        void 이미_대기중이면_409() throws Exception {
            given(waitlistService.register(2L, 10L))
                    .willThrow(new BusinessException(ErrorCode.ALREADY_IN_WAITLIST));

            mockMvc.perform(post("/classes/10/waitlist")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_IN_WAITLIST"));
        }

        @Test
        void X_User_Id_누락_시_400() throws Exception {
            mockMvc.perform(post("/classes/10/waitlist"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    class Cancel {

        @Test
        void 정상_철회_204() throws Exception {
            doNothing().when(waitlistService).cancelWaitlist(2L, 10L);

            mockMvc.perform(delete("/classes/10/waitlist/me")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isNoContent());
        }

        @Test
        void 대기열_미존재_시_404() throws Exception {
            willThrow(new BusinessException(ErrorCode.WAITLIST_NOT_FOUND))
                    .given(waitlistService).cancelWaitlist(2L, 10L);

            mockMvc.perform(delete("/classes/10/waitlist/me")
                            .header("X-User-Id", 2L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("WAITLIST_NOT_FOUND"));
        }

        @Test
        void 소유자_불일치_시_403() throws Exception {
            willThrow(new BusinessException(ErrorCode.NOT_WAITLIST_OWNER))
                    .given(waitlistService).cancelWaitlist(999L, 10L);

            mockMvc.perform(delete("/classes/10/waitlist/me")
                            .header("X-User-Id", 999L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_WAITLIST_OWNER"));
        }
    }
}

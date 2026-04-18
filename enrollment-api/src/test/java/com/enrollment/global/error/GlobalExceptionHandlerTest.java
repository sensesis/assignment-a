package com.enrollment.global.error;

import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import com.enrollment.domain.classes.service.ClassService;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    ClassService classService;

    @Test
    void BusinessException_시_ErrorCode의_HttpStatus와_포맷으로_응답() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("정원이 초과되었습니다"))
                .andExpect(jsonPath("$.path").value("/test/business"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void Valid_실패_시_400_INVALID_INPUT_필드에러_집약_메시지() throws Exception {
        String body = "{\"title\":\"\",\"price\":-1}";
        mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message", containsString("price")))
                .andExpect(jsonPath("$.message", containsString("title")))
                .andExpect(jsonPath("$.path").value("/test/valid"));
    }

    @Test
    void 예상외_예외_시_500_INTERNAL_ERROR() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/test/unexpected"));
    }

    @Test
    void PropertyReferenceException_시_400_INVALID_INPUT() throws Exception {
        mockMvc.perform(get("/test/bad-sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        public void business() {
            throw new BusinessException(ErrorCode.CAPACITY_EXCEEDED);
        }

        @PostMapping("/valid")
        public void valid(@RequestBody @Valid Payload payload) {
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/bad-sort")
        public void badSort() {
            throw new org.springframework.data.mapping.PropertyReferenceException(
                    "[\"string\"]",
                    org.springframework.data.util.TypeInformation.of(Object.class),
                    java.util.Collections.emptyList());
        }

        record Payload(@NotBlank String title, @Positive int price) {
        }
    }
}

package com.enrollment.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("수강 신청 시스템 API")
                        .version("v1.0")
                        .description("Spring Boot 3.3 기반 수강 신청 시스템 백엔드 API 문서"));
    }
}

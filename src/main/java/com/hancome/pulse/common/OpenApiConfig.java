package com.hancome.pulse.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Swagger UI 우측 상단 "Authorize"에서 넣은 토큰이 Authorization: Bearer <token> 로 붙게 하는 스킴 이름.
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI pulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pulse API")
                        .description("실시간 이벤트 피드백 모니터링 백엔드 API")
                        .version("v1"))
                // 전역 보안 요구: 문서의 모든 엔드포인트에 Bearer 토큰 입력란을 노출(공개 엔드포인트도 표시만 됨).
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}

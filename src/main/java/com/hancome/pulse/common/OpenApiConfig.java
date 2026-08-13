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

    // 인증은 HttpOnly 쿠키(accessToken)로 한다. JwtAuthenticationFilter도 이 쿠키만 읽으므로,
    // 문서의 보안 스킴을 apiKey(in cookie)로 선언해 docs/openapi.yaml의 cookieAuth와 일치시킨다.
    private static final String COOKIE_SCHEME = "cookieAuth";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";

    @Bean
    OpenAPI pulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Pulse API")
                        .description("실시간 이벤트 피드백 모니터링 백엔드 API")
                        .version("v1"))
                // 전역 보안 요구: 모든 엔드포인트에 cookieAuth 표시(공개 엔드포인트는 @Operation(security={})로 해제).
                // 쿠키는 로그인 시 Set-Cookie로 발급돼 브라우저가 자동 전송하므로 Swagger에서 별도 입력은 필요 없다.
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                COOKIE_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(ACCESS_TOKEN_COOKIE)));
    }
}

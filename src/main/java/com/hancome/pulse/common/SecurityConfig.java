package com.hancome.pulse.common;

import com.hancome.pulse.auth.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /**
     * 보안 필터 체인: stateless + JWT, CORS 허용, 공개 경로 외 인증 요구, 필터단 실패를 공통 봉투로 응답.
     *
     * @param http 시큐리티 빌더
     * @param jwtAuthenticationFilter 토큰을 검증해 SecurityContext를 채우는 필터
     * @return 구성된 필터 체인
     * @throws Exception 빌드 실패 시
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable()) // REST API라 CSRF 토큰 불필요
                .cors(Customizer.withDefaults()) // 아래 corsConfigurationSource 빈을 적용
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll() // 헬스체크·인증·Swagger 문서만 공개
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*")
                        .permitAll() // 이벤트 단건 공개 조회. 목록(GET /events)·쓰기는 인증 유지
                        .anyRequest()
                        .authenticated())
                // 필터 단에서 나는 인증/인가 실패는 @RestControllerAdvice에 안 잡히므로 여기서 같은 봉투로 응답.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                            res.setHeader("WWW-Authenticate", "Bearer"); // RFC 7235: 401은 인증 방식(Bearer)을 알린다
                            writeError(res, ErrorCode.UNAUTHORIZED);
                        })
                        .accessDeniedHandler((req, res, e) -> writeError(res, ErrorCode.NOT_OWNER)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 필터 단 인증/인가 실패 응답을 {@code {code, message}} 봉투 JSON으로 직접 쓴다.
     *
     * <p>봉투는 통제된 고정 문자열(enum 이름 + 기본 메시지)뿐이라 문자열로 조립한다. (Boot 4는 Jackson 3라 옛 ObjectMapper 주입이 안
     * 됨.) ErrorCode 메시지에 큰따옴표/역슬래시를 넣지 말 것(넣으면 JSON 이스케이프 필요).
     *
     * @param res 서블릿 응답
     * @param code 응답할 에러 코드
     * @throws IOException 응답 쓰기 실패 시
     */
    private static void writeError(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.defaultMessage() + "\"}");
    }

    /**
     * CORS 허용 정책. 허용 origin은 프로퍼티 {@code cors.allowed-origins}(쉼표 구분)로 주입한다.
     * 와일드카드({@code *.vercel.app}) 대신 정확한 도메인을 써야 임의 배포 사이트가 인증 응답(JWT 바디)을 읽는 것을 막는다(CWE-942).
     *
     * @param allowedOrigins 허용할 origin 목록(로컬 기본값, 배포는 env로 실제 도메인 주입)
     * @return {@code /**} 전 경로에 적용되는 CORS 설정 소스
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins); // 정확한 도메인(로컬은 http://localhost:*)
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); // Authorization, X-Client-Id 등 모두 허용
        // JWT는 Authorization 헤더로 보내고 쿠키를 쓰지 않으므로 credentials 불필요.
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * @return 비밀번호 해시·검증에 쓰는 BCrypt 인코더
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

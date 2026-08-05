package com.hancome.pulse.common;

import com.hancome.pulse.auth.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable()) // REST API라 CSRF 토큰 불필요
                .cors(Customizer.withDefaults()) // 아래 corsConfigurationSource 빈을 적용
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/auth/**")
                        .permitAll() // 헬스체크·인증만 공개
                        .anyRequest()
                        .authenticated())
                // 필터 단에서 나는 인증/인가 실패는 @RestControllerAdvice에 안 잡히므로 여기서 같은 봉투로 응답.
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint((req, res, e) -> writeError(res, ErrorCode.UNAUTHORIZED))
                                .accessDeniedHandler((req, res, e) -> writeError(res, ErrorCode.NOT_OWNER)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // 봉투는 우리가 통제하는 고정 문자열(enum 이름 + 기본 메시지)뿐이라 JSON을 직접 조립한다.
    // (Boot 4는 Jackson 3라 옛 ObjectMapper 주입이 안 됨 → 문자열 조립이 더 단순·안전.)
    // ErrorCode 메시지에 큰따옴표/역슬래시를 넣지 말 것(넣으면 JSON 이스케이프 필요).
    private static void writeError(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.defaultMessage() + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 로컬 개발 + Vercel(프리뷰 URL은 매번 바뀌므로 패턴). 고정 프로덕션 도메인 확정되면 추가.
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.vercel.app"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); // Authorization, X-Client-Id 등 모두 허용
        // JWT는 Authorization 헤더로 보내고 쿠키를 쓰지 않으므로 credentials 불필요.
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

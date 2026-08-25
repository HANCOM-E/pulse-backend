package com.hancome.pulse.common;

import com.hancome.pulse.auth.AuthCookies;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
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
    SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, AuthCookieProperties cookieProperties)
            throws Exception {
        // CSRF: 인증을 쿠키로 하므로 double-submit 토큰이 필요하다. XSRF-TOKEN 쿠키(비-HttpOnly, JS가 읽음)의
        // Secure·SameSite는 accessToken 쿠키와 동일 정책으로(환경별). FE는 그 값을 X-XSRF-TOKEN 헤더로 되돌려보낸다.
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(
                cookie -> cookie.sameSite(cookieProperties.sameSite()).secure(cookieProperties.secure()));

        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // 로그인·회원가입은 아직 토큰이 없는 진입점이라 CSRF 예외. 소감 제출은 비인증 공개(보호할 세션 없음)라 예외.
                        // 나머지 상태변경은 토큰 필요.
                        .ignoringRequestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                AuthCookies.REFRESH_TOKEN_PATH,
                                "/api/v1/events/*/feedbacks",
                                "/api/v1/events/*/games/*/participants")) // 게스트 참가는 비인증 공개(보호할 세션 없음)
                .cors(Customizer.withDefaults()) // 아래 corsConfigurationSource 빈을 적용
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/api/v1/health", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll() // 헬스체크·Swagger 문서 공개
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/logout",
                                AuthCookies.REFRESH_TOKEN_PATH)
                        .permitAll() // 로그인·회원가입·로그아웃·재발급은 공개(/auth/me는 인증 필요 → anyRequest로 빠짐)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*")
                        .permitAll() // 이벤트 단건 공개 조회. 목록(GET /events)·쓰기는 인증 유지
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions")
                        .permitAll() // 세션 목록 공개 조회. 세션 쓰기는 인증 유지
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/sessions/stream")
                        .permitAll() // 세션 목록 SSE 공개(게스트가 세션 열림을 실시간 수신)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/feedbacks")
                        .permitAll() // 소감 제출 공개(게스트)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/feedbacks")
                        .permitAll() // 집계 스냅샷 공개 조회
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/feedbacks/stream")
                        .permitAll() // 집계 SSE 공개 스트림
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/report")
                        .permitAll() // 리포트 공개 조회(auth 인식: 소유자 전체 / 게스트 공개분)
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/games/*")
                        .permitAll() // 게임 현재/단건 공개 조회(목록 GET .../games는 매칭 안 됨 → 주최자 인증 유지)
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/*/games/*/participants")
                        .permitAll() // 게스트 참가/재참가 공개
                        .anyRequest()
                        .authenticated())
                // 필터 단에서 나는 인증/인가 실패는 @RestControllerAdvice에 안 잡히므로 여기서 같은 봉투로 응답.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                                (req, res, e) -> writeError(res, ErrorCode.UNAUTHORIZED))
                        // CSRF 실패(토큰 없음/불일치)와 소유권 실패를 구분해 응답한다.
                        .accessDeniedHandler((req, res, e) -> writeError(
                                res, e instanceof CsrfException ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.NOT_OWNER)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 지연 CSRF 토큰을 강제 렌더해 XSRF-TOKEN 쿠키가 응답에 실리게 한다.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
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
        // setAllowedOriginPatterns는 credentials=true와 함께 써도, 매칭된 "실제 origin"을 반사해 내려준다
        // (Access-Control-Allow-Origin에 * 대신 http://localhost:3000이 실림). setAllowedOrigins("*")는 credentials와 금지.
        config.setAllowedOriginPatterns(allowedOrigins); // 정확한 도메인(로컬은 http://localhost:*)
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*")); // X-Client-Id, X-XSRF-TOKEN 등 모두 허용
        // 인증을 HttpOnly 쿠키로 하므로 자격증명 동반 요청을 허용해야 한다.
        // → 응답에 Access-Control-Allow-Credentials: true 가 실려야 브라우저가 쿠키를 주고받는다.
        config.setAllowCredentials(true);
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

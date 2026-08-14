package com.hancome.pulse.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청/응답 메타를 한 줄씩 남기는 액세스 로그. 배포 서버(Render) 로그에서 실제 트래픽과 에러 흐름을 눈으로 좇기 위한 최소 로깅.
 *
 * <p>본문·헤더·쿼리스트링은 남기지 않는다 — 소감 텍스트·비밀번호·쿠키·토큰이 로그로 새는 것을 원천 차단한다.
 *
 * <p>{@code HIGHEST_PRECEDENCE}로 등록해 Spring Security 필터 체인({@code FilterChainProxy}, order -100)보다 먼저
 * 실행된다. 그래야 시큐리티가 자체 거부한 요청(401·403·CSRF)도 이 필터의 {@code finally}까지 돌아와 최종 status가 찍힌다 —
 * 그게 배포 로그에서 제일 보고 싶은 "에러"라서. 경과시간은 벽시계(NTP로 튐)가 아닌 {@link System#nanoTime()}(모노토닉)으로 잰다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // 헬스체크(Render 주기 호출)와 CORS 프리플라이트(OPTIONS)는 로그에서 뺀다 — 앱 트래픽이 아니라 잡음이다.
        return "/api/v1/health".equals(req.getRequestURI()) || "OPTIONS".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        // [REQ]는 "응답 없이 죽은 요청"을 볼 때만 필요해 DEBUG로 둔다. 평상시 INFO엔 status·소요시간이 다 담긴 [RES] 한 줄만.
        log.debug("[REQ] {} {}", req.getMethod(), req.getRequestURI());
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("[RES] {} {} -> {} ({}ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
        }
    }
}

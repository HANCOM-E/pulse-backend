package com.hancome.pulse.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청/응답 메타를 한 줄씩 남기는 액세스 로그. 배포 서버(Render) 로그에서 실제 트래픽과 에러 흐름을 눈으로 좇기 위한 최소 로깅.
 *
 * <p>본문·헤더·쿼리스트링은 남기지 않는다 — 소감 텍스트·비밀번호·쿠키·토큰이 로그로 새는 것을 원천 차단한다. 상태 코드는 필터 체인이 끝난 뒤
 * ({@code finally}) 읽으므로 시큐리티·핸들러가 확정한 최종 값이다.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        log.info("[REQ] {} {}", req.getMethod(), req.getRequestURI());
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = System.currentTimeMillis() - start;
            log.info("[RES] {} {} -> {} ({}ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), ms);
        }
    }
}

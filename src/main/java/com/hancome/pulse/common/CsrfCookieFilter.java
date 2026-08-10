package com.hancome.pulse.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * deferred CSRF 토큰을 매 요청 강제로 로드해 {@code XSRF-TOKEN} 쿠키가 실제 응답에 실리게 한다.
 *
 * <p>Spring Security 6는 CSRF 토큰을 지연 로드해서, 아무도 {@code getToken()}을 호출하지 않으면 쿠키가 안 나가는 함정이 있다. 이
 * 필터가 그걸 건드려 쿠키 기록을 보장한다(BasicAuthenticationFilter 뒤에 배치).
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken != null) {
            csrfToken.getToken(); // 지연 토큰을 렌더 → 쿠키에 기록
        }
        filterChain.doFilter(request, response);
    }
}

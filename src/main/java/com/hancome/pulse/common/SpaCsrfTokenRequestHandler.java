package com.hancome.pulse.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * SPA용 CSRF 토큰 핸들러(Spring Security 공식 레시피).
 *
 * <p>응답 렌더는 XOR로(BREACH 방어 — 응답마다 마스킹된 다른 값), 요청 검증은 헤더로 온 raw 토큰이면 평문으로 처리한다. FE는 {@code
 * XSRF-TOKEN} 쿠키의 raw 값을 그대로 {@code X-XSRF-TOKEN} 헤더에 실어 되돌려보낸다.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        // 렌더는 XOR로. deferred 토큰을 강제 로드해 쿠키에 실리게 한다.
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // 헤더로 오면 평문(SPA가 쿠키 raw 값 그대로 전송), 폼 파라미터면 XOR.
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}

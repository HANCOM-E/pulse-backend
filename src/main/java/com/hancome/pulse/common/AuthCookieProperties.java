package com.hancome.pulse.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인증(accessToken)·CSRF(XSRF-TOKEN) 쿠키의 환경별 속성.
 *
 * <p>로컬(http, FE/BE 모두 localhost = same-site)은 {@code Secure=false}·{@code SameSite=Lax}, 배포(https,
 * Vercel↔Render = cross-site)는 env로 {@code Secure=true}·{@code SameSite=None}. 두 쿠키(AuthController·SecurityConfig)가
 * 같은 값을 쓰도록 여기서 한 번만 주입한다.
 *
 * <p>주의: {@code SameSite=None}은 {@code Secure=true}가 필수다(브라우저 규칙). 둘을 짝맞춰 설정할 것.
 */
@Component
public class AuthCookieProperties {
    private final boolean secure;
    private final String sameSite;

    public AuthCookieProperties(
            @Value("${auth.cookie.secure}") boolean secure, @Value("${auth.cookie.same-site}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public boolean secure() {
        return secure;
    }

    public String sameSite() {
        return sameSite;
    }
}

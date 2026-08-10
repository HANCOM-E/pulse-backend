package com.hancome.pulse.common;

import java.util.Set;
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
    private static final Set<String> ALLOWED_SAME_SITE = Set.of("None", "Lax", "Strict");

    private final boolean secure;
    private final String sameSite;

    public AuthCookieProperties(
            @Value("${auth.cookie.secure}") boolean secure, @Value("${auth.cookie.same-site}") String sameSite) {
        // fail-closed: 잘못된 값으로 조용히 뜨지 않고 부팅을 막는다.
        if (!ALLOWED_SAME_SITE.contains(sameSite)) {
            throw new IllegalStateException("auth.cookie.same-site는 None|Lax|Strict 중 하나여야 합니다. 실제값: " + sameSite);
        }
        // SameSite=None은 Secure=true가 필수(브라우저가 None+비Secure 쿠키를 거부). 배포에서 env 짝을 놓치는 실수 차단.
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None은 Secure=true가 필요합니다(AUTH_COOKIE_SECURE=true 설정 필요).");
        }
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

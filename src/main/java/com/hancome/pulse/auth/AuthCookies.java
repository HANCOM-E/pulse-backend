package com.hancome.pulse.auth;

/**
 * 인증 쿠키의 이름·경로 상수. 필터(읽기)·컨트롤러(발급)·시큐리티(매처)·Swagger 스킴이 같은 값을 써야 하는 공유 계약이라 한곳에 둔다.
 */
public final class AuthCookies {
    private AuthCookies() {}

    /** 액세스 토큰 쿠키 이름(HttpOnly). 필터가 읽고 컨트롤러가 발급한다. */
    public static final String ACCESS_TOKEN_COOKIE = "accessToken";

    /** 리프레시 토큰 쿠키 이름(HttpOnly). */
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /**
     * 리프레시 엔드포인트의 백엔드 라우트 경로(시큐리티 매처·CSRF 예외가 이 값을 공유). 쿠키 Path 속성으로는 쓰지 않는다 — 브라우저는
     * Next 프록시(/api/proxy/**)로만 요청해 이 경로로 좁히면 쿠키가 안 실리므로, refreshToken 쿠키는 accessToken과 같이 Path=/로 발급한다.
     */
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth/refresh";
}

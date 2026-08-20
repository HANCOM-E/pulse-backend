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

    /** 리프레시 쿠키·엔드포인트 경로. 쿠키를 이 경로로 좁혀 다른 요청엔 안 실리게 하고, 시큐리티 매처도 같은 값을 써 일치를 강제한다. */
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth/refresh";
}

package com.hancome.pulse.auth;

import com.hancome.pulse.auth.dto.*;
import com.hancome.pulse.common.AuthCookieProperties;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieProperties cookieProperties;

    public AuthController(AuthService authService, AuthCookieProperties cookieProperties) {
        this.authService = authService;
        this.cookieProperties = cookieProperties;
    }

    private ResponseCookie accessTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AuthCookies.ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie refreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AuthCookies.REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                // Path=/ 로 둔다: 브라우저는 Next 프록시(/api/proxy/**)로만 요청하므로, 쿠키 Path를 백엔드
                // 라우트(/api/v1/auth/refresh)로 좁히면 브라우저 요청 경로와 안 맞아 쿠키가 아예 안 실린다.
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    // 액세스+리프레시 쿠키를 함께 실은 응답을 만든다(로그인·가입·재발급 공통).
    private ResponseEntity<AuthUser> withTokenCookies(HttpStatus status, AuthResult r) {
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        accessTokenCookie(r.accessToken(), r.accessExpiresInSeconds())
                                .toString())
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie(r.refreshToken(), r.refreshExpiresInSeconds())
                                .toString())
                .body(r.user());
    }

    @Operation(security = {}) // 공개 진입점 — 전역 cookieAuth 요구 해제
    @PostMapping("/signup")
    public ResponseEntity<AuthUser> signup(@Valid @RequestBody SignupRequest req) {
        return withTokenCookies(HttpStatus.CREATED, authService.signUp(req));
    }

    @Operation(security = {}) // 공개 진입점
    @PostMapping("/login")
    public ResponseEntity<AuthUser> login(@Valid @RequestBody LoginRequest req) {
        return withTokenCookies(HttpStatus.OK, authService.login(req));
    }

    @Operation(security = {}) // 리프레시 쿠키만으로 재발급(액세스 토큰 만료 후 호출)
    @PostMapping("/refresh")
    public ResponseEntity<AuthUser> refresh(
            @CookieValue(value = AuthCookies.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        return withTokenCookies(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @Operation(security = {}) // 로그아웃은 토큰 없이도 호출 가능(쿠키 만료)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie("", 0).toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie("", 0).toString())
                .build();
    }

    @GetMapping("/me")
    public AuthUser me(@AuthenticationPrincipal Long userId) {
        return authService.getUser(userId);
    }
}

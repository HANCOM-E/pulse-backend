package com.hancome.pulse.auth;

import com.hancome.pulse.auth.dto.*;
import com.hancome.pulse.common.AuthCookieProperties;
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
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthUser> signup(@Valid @RequestBody SignupRequest req) {
        AuthResult authResult = authService.signUp(req);
        ResponseCookie cookie = accessTokenCookie(authResult.token(), authResult.expiresInSeconds());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResult.user());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUser> login(@Valid @RequestBody LoginRequest req) {
        AuthResult authResult = authService.login(req);
        ResponseCookie cookie = accessTokenCookie(authResult.token(), authResult.expiresInSeconds());

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResult.user());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = accessTokenCookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public AuthUser me(@AuthenticationPrincipal Long userId) {
        return authService.getUser(userId);
    }
}

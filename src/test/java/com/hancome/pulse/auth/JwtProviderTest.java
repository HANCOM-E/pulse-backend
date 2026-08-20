package com.hancome.pulse.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {
    // HS256은 secret 32바이트 이상 요구. 액세스 1h, 리프레시 14d.
    private final JwtProvider jwt =
            new JwtProvider("test-secret-key-that-is-at-least-32-bytes-long!!", 3_600_000L, 1_209_600_000L);

    @Test
    void roundTripsEachTokenType() {
        assertEquals(7L, jwt.parseUserId(jwt.generateAccessToken(7L)));
        assertEquals(7L, jwt.parseRefreshUserId(jwt.generateRefreshToken(7L)));
    }

    @Test
    void rejectsCrossTypeUse() {
        // 리프레시 토큰을 액세스로, 액세스 토큰을 리프레시로 쓰면 거부 — 토큰 혼동 차단
        assertThrows(JwtException.class, () -> jwt.parseUserId(jwt.generateRefreshToken(7L)));
        assertThrows(JwtException.class, () -> jwt.parseRefreshUserId(jwt.generateAccessToken(7L)));
    }
}

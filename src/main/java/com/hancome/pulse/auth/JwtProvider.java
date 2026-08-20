package com.hancome.pulse.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {
    // 토큰 용도를 type 클레임으로 구분해, 리프레시 토큰을 액세스 토큰으로(또는 반대로) 재사용하는 혼동을 차단한다.
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(Long userId) {
        return generate(userId, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(Long userId) {
        return generate(userId, TYPE_REFRESH, refreshExpirationMs);
    }

    /** 액세스 토큰에서 userId를 꺼낸다(리프레시 토큰이면 거부). */
    public Long parseUserId(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** 리프레시 토큰에서 userId를 꺼낸다(액세스 토큰이면 거부). */
    public Long parseRefreshUserId(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public long getAccessExpirationInSeconds() {
        return accessExpirationMs / 1000;
    }

    public long getRefreshExpirationInSeconds() {
        return refreshExpirationMs / 1000;
    }

    private String generate(Long userId, String type, long ttlMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(key, Jwts.SIG.HS256) // 알고리즘을 HS256으로 고정(secret 길이에 따라 HS384로 추론되는 것 방지)
                .compact();
    }

    private Long parse(String token, String expectedType) {
        Claims claims =
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("예상한 토큰 타입이 아닙니다: " + expectedType);
        }
        return Long.parseLong(claims.getSubject());
    }
}

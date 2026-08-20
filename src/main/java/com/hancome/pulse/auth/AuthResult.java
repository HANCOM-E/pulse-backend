package com.hancome.pulse.auth;

import com.hancome.pulse.auth.dto.AuthUser;

public record AuthResult(
        AuthUser user,
        String accessToken,
        long accessExpiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds) {}

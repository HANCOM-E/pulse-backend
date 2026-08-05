package com.hancome.pulse.auth.dto;

import java.time.Instant;

/** 회원가입 응답(팀 API 명세서 확정). 가입과 동시에 accessToken을 발급해 자동 로그인시킨다. */
public record SignupResponse(Long id, String email, Instant createdAt, String accessToken, long expiresIn) {}

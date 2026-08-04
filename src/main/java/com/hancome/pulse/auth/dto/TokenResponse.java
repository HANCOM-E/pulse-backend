package com.hancome.pulse.auth.dto;

public record TokenResponse(String accessToken, long expiresIn) {}

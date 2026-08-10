package com.hancome.pulse.auth.dto;

import java.time.Instant;

public record AuthUser(Long id, String email, Instant createdAt) {}

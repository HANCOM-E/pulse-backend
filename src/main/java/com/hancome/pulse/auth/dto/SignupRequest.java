package com.hancome.pulse.auth.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Size(min = 8, max = 32)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다")
        String password) {}

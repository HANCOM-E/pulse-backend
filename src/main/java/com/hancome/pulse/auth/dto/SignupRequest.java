package com.hancome.pulse.auth.dto;

import jakarta.validation.constraints.*;

/** 회원가입 요청. 이메일 형식과 비밀번호 정책(8~32자, 영문+숫자 필수, 특수문자 허용)을 검증한다. */
public record SignupRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Size(min = 8, max = 32)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다")
        String password) {}

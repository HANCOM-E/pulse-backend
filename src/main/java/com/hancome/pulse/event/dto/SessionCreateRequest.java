package com.hancome.pulse.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 세션 생성 요청. 제목·순서 필수. 상태는 요청에 없고 서버가 생성 시 CLOSED로 둔다(진행 시점에 소유자가 ACTIVE로 연다). */
public record SessionCreateRequest(
        @NotBlank String title, @NotNull Integer order) {}

package com.hancome.pulse.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 이벤트 생성 요청. 제목 2~60자 필수, 설명 최대 500자(선택), 행사 날짜 필수. */
public record EventCreateRequest(
        @NotBlank @Size(min = 2, max = 60) String title,
        @Size(max = 500) String description,
        @NotNull LocalDate eventDate) {}

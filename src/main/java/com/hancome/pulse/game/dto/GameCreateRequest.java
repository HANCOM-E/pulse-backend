package com.hancome.pulse.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게임 생성 요청(주최자). 제목 1~50자 필수. 종류는 현재 PINBALL 고정이라 서버가 지정한다. */
public record GameCreateRequest(
        @NotBlank @Size(min = 1, max = 50) String title) {}

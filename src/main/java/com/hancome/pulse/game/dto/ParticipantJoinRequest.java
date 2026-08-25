package com.hancome.pulse.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게임 참가/재참가 요청(게스트). 닉네임 1~12자 필수. 식별은 본문이 아니라 {@code X-Client-Id} 헤더로 한다. */
public record ParticipantJoinRequest(
        @NotBlank @Size(min = 1, max = 12) String nickname) {}

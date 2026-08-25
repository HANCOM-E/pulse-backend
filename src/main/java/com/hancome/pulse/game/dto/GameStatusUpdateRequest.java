package com.hancome.pulse.game.dto;

import com.hancome.pulse.game.GameStatus;
import jakarta.validation.constraints.NotNull;

/** 게임 상태 전이 요청(주최자). 전이할 목표 상태. 전이 유효성은 서비스의 상태머신 가드가 검증한다. */
public record GameStatusUpdateRequest(@NotNull GameStatus status) {}

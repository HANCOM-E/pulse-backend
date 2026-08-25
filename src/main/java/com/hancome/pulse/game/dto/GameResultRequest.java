package com.hancome.pulse.game.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 게임 결과(순위) 제출 요청(주최자). {@code ranking}은 참가자 PK를 등수 순서(1등이 첫 원소)로 담는다.
 *
 * <p>서버는 순위 자체를 계산하지 않고(A안), 이 배열의 원소가 모두 해당 게임 소속 참가자인지와 중복이 없는지만 검증한다.
 */
public record GameResultRequest(@NotEmpty List<@NotNull Long> ranking) {}

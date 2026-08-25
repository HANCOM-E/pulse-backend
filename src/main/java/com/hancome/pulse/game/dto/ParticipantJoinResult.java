package com.hancome.pulse.game.dto;

/**
 * 참가 처리 결과(서비스→컨트롤러 내부 전달용). {@code created}로 신규(201)와 재참가·닉네임 갱신(200)을 구분해 컨트롤러가 상태코드를
 * 정한다. 응답 본문으로는 {@code participant}만 나간다.
 */
public record ParticipantJoinResult(ParticipantView participant, boolean created) {}

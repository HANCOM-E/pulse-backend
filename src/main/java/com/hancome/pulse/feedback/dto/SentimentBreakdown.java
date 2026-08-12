package com.hancome.pulse.feedback.dto;

/**
 * 감정 분포 집계({@code sentiment != UNKNOWN}인 소감만). UNKNOWN 건수는 스냅샷의 {@code unclassifiedCount}로 따로 센다.
 *
 * <p>JSON 키가 계약상 대문자({@code POS/NEU/NEG})라 record 컴포넌트명도 대문자로 둔다(Jackson이 컴포넌트명을 그대로 직렬화).
 *
 * @param POS 긍정 건수
 * @param NEU 중립 건수
 * @param NEG 부정 건수
 */
public record SentimentBreakdown(int POS, int NEU, int NEG) {}

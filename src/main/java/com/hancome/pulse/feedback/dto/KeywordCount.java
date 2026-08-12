package com.hancome.pulse.feedback.dto;

/**
 * 키워드별 빈도(집계 결과의 한 항목). 집계 쿼리가 {@code new KeywordCount(keyword, count(...))} 형태로 직접 생성할 수 있도록 필드 순서를
 * keyword·count로 둔다. (JPQL {@code count()}는 Long이므로 constructor expression에서 타입이 안 맞으면 cast 필요.)
 *
 * @param keyword 키워드
 * @param count 등장 횟수
 */
public record KeywordCount(String keyword, int count) {}

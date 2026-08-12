package com.hancome.pulse.feedback.dto;

import java.util.List;

/**
 * 실시간 집계 스냅샷(폴링·SSE 공통 모양). {@code VISIBLE} 소감만 서버에서 집계한다.
 *
 * @param sentimentBreakdown POS/NEU/NEG 분포(UNKNOWN 제외)
 * @param unclassifiedCount sentiment = UNKNOWN 건수
 * @param topKeywords 빈도순 상위 10
 * @param recentFeedbacks 최신 50(FeedbackView)
 */
public record FeedbackSnapshot(
        SentimentBreakdown sentimentBreakdown,
        int unclassifiedCount,
        List<KeywordCount> topKeywords,
        List<FeedbackView> recentFeedbacks) {}

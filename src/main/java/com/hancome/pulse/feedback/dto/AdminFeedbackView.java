package com.hancome.pulse.feedback.dto;

import com.hancome.pulse.feedback.Feedback;
import com.hancome.pulse.feedback.FeedbackStatus;
import com.hancome.pulse.feedback.Sentiment;
import java.time.Instant;
import java.util.List;

/**
 * 관리자(소유자) 전용 소감 풀뷰. 공개 {@link FeedbackView}에 모더레이션 필드(toxic·taggerVersion·status)를 더한다. /admin/*
 * 응답에만 쓴다.
 */
public record AdminFeedbackView(
        Long id,
        Long sessionId,
        String text,
        Sentiment sentiment,
        boolean toxic,
        List<String> keywords,
        String taggerVersion,
        FeedbackStatus status,
        Instant createdAt) {

    /**
     * 엔티티 → 관리자 뷰 매핑. {@code session}은 지연 로딩이므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param f 소감 엔티티
     * @return 관리자 풀뷰 DTO
     */
    public static AdminFeedbackView from(Feedback f) {
        return new AdminFeedbackView(
                f.getId(),
                f.getSession().getId(),
                f.getText(),
                f.getSentiment(),
                f.getToxic(),
                f.getKeywords(),
                f.getTaggerVersion(),
                f.getStatus(),
                f.getCreatedAt());
    }
}

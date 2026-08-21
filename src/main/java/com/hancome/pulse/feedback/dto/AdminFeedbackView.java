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
     * 엔티티 → 관리자 뷰 매핑. {@code session}·{@code keywords}는 지연 로딩이므로 트랜잭션 안에서 호출해야 한다. {@code
     * keywords}는 {@link List#copyOf}로 즉시 복사해, 트랜잭션(=OSIV) 밖(예: SSE 비동기 직렬화)에서 지연 초기화가 터지지 않게 한다.
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
                List.copyOf(f.getKeywords()),
                f.getTaggerVersion(),
                f.getStatus(),
                f.getCreatedAt());
    }
}

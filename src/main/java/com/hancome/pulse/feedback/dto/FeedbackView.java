package com.hancome.pulse.feedback.dto;

import com.hancome.pulse.feedback.Feedback;
import com.hancome.pulse.feedback.Sentiment;
import java.time.Instant;
import java.util.List;

/** 공개용 소감 뷰(모더레이션 필드 toxic·taggerVersion·status 제외). 제출 응답·집계 recentFeedbacks에 쓰인다. */
public record FeedbackView(
        Long id, Long sessionId, String text, Sentiment sentiment, List<String> keywords, Instant createdAt) {

    /**
     * 엔티티 → 공개 뷰 매핑. {@code session}·{@code keywords}는 지연 로딩이므로 트랜잭션 안에서 호출해야 한다. {@code keywords}는
     * {@link List#copyOf}로 즉시 복사해, 트랜잭션(=OSIV) 밖(예: SSE 비동기 직렬화)에서 지연 초기화가 터지지 않게 한다.
     *
     * @param f 소감 엔티티
     * @return 공개 뷰 DTO
     */
    public static FeedbackView from(Feedback f) {
        return new FeedbackView(
                f.getId(),
                f.getSession().getId(),
                f.getText(),
                f.getSentiment(),
                List.copyOf(f.getKeywords()),
                f.getCreatedAt());
    }
}

package com.hancome.pulse.feedback.dto;

import com.hancome.pulse.feedback.Feedback;
import java.util.List;

/** 관리자 소감 목록 봉투(페이지네이션 seam). {@code items}는 {@link AdminFeedbackView}. */
public record FeedbackListResponse(List<AdminFeedbackView> items) {

    /**
     * @param feedbacks 소감 엔티티 목록
     * @return 관리자 뷰로 매핑한 목록 봉투
     */
    public static FeedbackListResponse from(List<Feedback> feedbacks) {
        return new FeedbackListResponse(
                feedbacks.stream().map(AdminFeedbackView::from).toList());
    }
}

package com.hancome.pulse.feedback;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.feedback.dto.AdminFeedbackView;
import com.hancome.pulse.feedback.dto.FeedbackListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소감 모더레이션(소유자 전용). 큐 조회 + 숨김/해제/삭제 상태 전이. 소유권은 {@code feedback → session → event → owner}로 한
 * 단계 더 타고 검증한다.
 */
@Service
public class AdminFeedbackService {
    private final FeedbackRepository feedbackRepository;

    public AdminFeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * 모더레이션 큐를 조회한다. 모든 필터는 optional이며 소유자 소유 소감으로 한정한다.
     *
     * @param ownerId 인증된 주최자 PK(자기 이벤트 소감만)
     * @param eventCode 특정 이벤트로 좁힘(null이면 내 전체 이벤트)
     * @param sessionId 특정 세션으로 좁힘(null이면 무관)
     * @param toxic 독성 여부 필터(null이면 무관)
     * @param includeHidden false면 HIDDEN 제외, true면 포함. DELETED는 항상 제외한다.
     * @return 관리자 뷰 목록 봉투
     */
    @Transactional(readOnly = true)
    public FeedbackListResponse list(
            Long ownerId, String eventCode, Long sessionId, Boolean toxic, boolean includeHidden) {
        return FeedbackListResponse.from(
                feedbackRepository.adminList(ownerId, eventCode, sessionId, toxic, includeHidden));
    }

    /** 소감을 숨긴다(VISIBLE/HIDDEN → HIDDEN). 이미 DELETED면 대상이 아니다. */
    @Transactional
    public AdminFeedbackView hide(Long ownerId, Long feedbackId) {
        Feedback f = loadOwnedFeedback(ownerId, feedbackId);
        if (f.getStatus() == FeedbackStatus.DELETED) throw new ApiException(ErrorCode.FEEDBACK_ALREADY_DELETED);
        f.setStatus(FeedbackStatus.HIDDEN);
        return AdminFeedbackView.from(f);
    }

    /** 소감 숨김을 해제한다(→ VISIBLE). 이미 DELETED면 대상이 아니다. */
    @Transactional
    public AdminFeedbackView show(Long ownerId, Long feedbackId) {
        Feedback f = loadOwnedFeedback(ownerId, feedbackId);
        if (f.getStatus() == FeedbackStatus.DELETED) throw new ApiException(ErrorCode.FEEDBACK_ALREADY_DELETED);
        f.setStatus(FeedbackStatus.VISIBLE);
        return AdminFeedbackView.from(f);
    }

    /** 소감을 소프트 삭제한다(→ DELETED). 이미 DELETED면 충돌. */
    @Transactional
    public AdminFeedbackView delete(Long ownerId, Long feedbackId) {
        Feedback f = loadOwnedFeedback(ownerId, feedbackId);
        if (f.getStatus() == FeedbackStatus.DELETED) throw new ApiException(ErrorCode.FEEDBACK_ALREADY_DELETED);
        f.setStatus(FeedbackStatus.DELETED);
        return AdminFeedbackView.from(f);
    }

    /**
     * 소감을 로드하고 그 소감이 속한 이벤트의 소유자인지 검증한다(모더레이션 공통 진입점).
     *
     * @param ownerId 요청자 PK
     * @param feedbackId 대상 소감 PK
     * @return 소유가 확인된 소감 엔티티
     * @throws ApiException 소감 없으면 {@code FEEDBACK_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    public Feedback loadOwnedFeedback(Long ownerId, Long feedbackId) {
        Feedback feedback = feedbackRepository
                .findById(feedbackId)
                .orElseThrow(() -> new ApiException(ErrorCode.FEEDBACK_NOT_FOUND));
        if (!feedback.getSession().getEvent().getOwner().getId().equals(ownerId))
            throw new ApiException(ErrorCode.NOT_OWNER);
        return feedback;
    }
}

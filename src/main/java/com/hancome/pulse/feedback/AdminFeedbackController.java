package com.hancome.pulse.feedback;

import com.hancome.pulse.feedback.dto.AdminFeedbackView;
import com.hancome.pulse.feedback.dto.FeedbackListResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소감 모더레이션 API(인증·소유자 전용). 큐 조회 + 숨김/해제/삭제. 유저 식별자는 {@code @AuthenticationPrincipal}로
 * SecurityContext에서 가져온다.
 */
@RestController
@RequestMapping("/api/v1/admin/feedbacks")
public class AdminFeedbackController {
    private final AdminFeedbackService adminFeedbackService;

    public AdminFeedbackController(AdminFeedbackService adminFeedbackService) {
        this.adminFeedbackService = adminFeedbackService;
    }

    @GetMapping
    public FeedbackListResponse list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Boolean toxic,
            @RequestParam(defaultValue = "false") boolean includeHidden) {
        return adminFeedbackService.list(userId, eventCode, sessionId, toxic, includeHidden);
    }

    @PatchMapping("/{feedbackId}/hide")
    public AdminFeedbackView hide(@AuthenticationPrincipal Long userId, @PathVariable Long feedbackId) {
        return adminFeedbackService.hide(userId, feedbackId);
    }

    @PatchMapping("/{feedbackId}/show")
    public AdminFeedbackView show(@AuthenticationPrincipal Long userId, @PathVariable Long feedbackId) {
        return adminFeedbackService.show(userId, feedbackId);
    }

    @PatchMapping("/{feedbackId}/delete")
    public AdminFeedbackView delete(@AuthenticationPrincipal Long userId, @PathVariable Long feedbackId) {
        return adminFeedbackService.delete(userId, feedbackId);
    }
}

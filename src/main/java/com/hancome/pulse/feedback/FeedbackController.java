package com.hancome.pulse.feedback;

import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import com.hancome.pulse.feedback.dto.FeedbackSubmitRequest;
import com.hancome.pulse.feedback.dto.FeedbackView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소감 제출·집계(둘 다 공개, 비인증). 유저 식별이 없으므로 레이트리밋 키는 {@code X-Client-Id} 헤더(FE localStorage UUID)를
 * 쓰고, 없으면 IP로 폴백한다.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/feedbacks")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(security = {}) // 공개 엔드포인트
    @PostMapping
    public ResponseEntity<FeedbackView> submit(
            @PathVariable String eventCode,
            @Valid @RequestBody FeedbackSubmitRequest req,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            HttpServletRequest servletRequest) {
        String rateKey = StringUtils.hasText(clientId) ? clientId : servletRequest.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(eventCode, req, rateKey));
    }

    @Operation(security = {}) // 공개 엔드포인트
    @GetMapping
    public FeedbackSnapshot snapshot(@PathVariable String eventCode, @RequestParam(required = false) Long sessionId) {
        return feedbackService.getSnapshot(eventCode, sessionId);
    }
}

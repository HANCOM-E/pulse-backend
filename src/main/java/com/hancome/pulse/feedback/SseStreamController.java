package com.hancome.pulse.feedback;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventRepository;
import com.hancome.pulse.event.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 스트림(SSE). 연결 시 현재 스냅샷을 보내고, 변경마다 갱신 스냅샷을 push한다(폴링 대체).
 *
 * <ul>
 *   <li>공개 집계: 소감 제출·모더레이션으로 갱신
 *   <li>공개 세션목록: 주최자가 세션을 열고/닫을 때 갱신(게스트가 새로고침 없이 반영)
 *   <li>관리자 큐: 소유자 전용, 소감 변경으로 갱신
 * </ul>
 */
@RestController
public class SseStreamController {
    private final SseHub sseHub;
    private final FeedbackService feedbackService;
    private final AdminFeedbackService adminFeedbackService;
    private final SessionService sessionService;
    private final EventRepository eventRepository;

    public SseStreamController(
            SseHub sseHub,
            FeedbackService feedbackService,
            AdminFeedbackService adminFeedbackService,
            SessionService sessionService,
            EventRepository eventRepository) {
        this.sseHub = sseHub;
        this.feedbackService = feedbackService;
        this.adminFeedbackService = adminFeedbackService;
        this.sessionService = sessionService;
        this.eventRepository = eventRepository;
    }

    @Operation(security = {}) // 공개 집계 스트림
    @GetMapping("/api/v1/events/{eventCode}/feedbacks/stream")
    public SseEmitter feedbackStream(
            @PathVariable String eventCode,
            @RequestParam(required = false) Long sessionId,
            HttpServletResponse response) {
        setStreamHeaders(response);
        return sseHub.subscribe(
                SseHub.feedbackChannel(eventCode), () -> feedbackService.getSnapshot(eventCode, sessionId));
    }

    @Operation(security = {}) // 공개 세션목록 스트림 — 세션 열림/닫힘을 게스트에 실시간 반영
    @GetMapping("/api/v1/events/{eventCode}/sessions/stream")
    public SseEmitter sessionStream(@PathVariable String eventCode, HttpServletResponse response) {
        setStreamHeaders(response);
        return sseHub.subscribe(SseHub.sessionsChannel(eventCode), () -> sessionService.listPublic(eventCode));
    }

    @GetMapping("/api/v1/admin/feedbacks/stream")
    public SseEmitter adminFeedbackStream(
            @AuthenticationPrincipal Long userId,
            @RequestParam String eventCode,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Boolean toxic,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            HttpServletResponse response) {
        assertOwnsEvent(userId, eventCode);
        setStreamHeaders(response);
        return sseHub.subscribe(
                SseHub.feedbackChannel(eventCode),
                () -> adminFeedbackService.list(userId, eventCode, sessionId, toxic, includeHidden));
    }

    // SSE가 프록시(Vercel/Next 엣지)에서 막히는 걸 방지한다. no-transform: 중간 계층 gzip 변형 금지(압축기가 스트림을
    // 버퍼에 가둬 EventSource가 OPEN까지만 되고 이벤트를 0건 받는 현상). X-Accel-Buffering: nginx류 프록시 응답 버퍼링 끔.
    private static void setStreamHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }

    // 관리자 스트림은 자기 이벤트만 구독 가능. 소감이 하나도 없어도 검증되게 이벤트 소유권을 직접 확인한다.
    // getOwner().getId()는 FK 값이라 프록시 초기화 없이 읽힌다(세션 불필요).
    private void assertOwnsEvent(Long userId, String eventCode) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(userId)) {
            throw new ApiException(ErrorCode.NOT_OWNER);
        }
    }
}

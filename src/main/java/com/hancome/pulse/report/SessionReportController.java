package com.hancome.pulse.report;

import com.hancome.pulse.report.dto.SessionReportGenerateRequest;
import com.hancome.pulse.report.dto.SessionReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 리포트 API. 생성·조회는 비인증(강연자용 링크로 접근), 리셋만 주최자 인증.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions/{sessionId}/report")
public class SessionReportController {
    private final SessionReportService sessionReportService;

    public SessionReportController(SessionReportService sessionReportService) {
        this.sessionReportService = sessionReportService;
    }

    // 비인증: 강연자가 대시보드 링크에서 자료요약을 실어 생성. 남용은 세션 CLOSED 게이트 + 멱등으로 방어(서비스).
    @Operation(security = {})
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED) // 202: 접수·처리 시작(GENERATING). 완료는 GET 폴링으로 확인.
    public SessionReportResponse generate(
            @PathVariable String eventCode,
            @PathVariable Long sessionId,
            @RequestBody(required = false) @Valid SessionReportGenerateRequest req) {
        String materialSummary = req == null ? null : req.materialSummary();
        return sessionReportService.generate(eventCode, sessionId, materialSummary);
    }

    @Operation(security = {}) // 공개 조회 — 세션 피드백 집계가 공개라 세션 리포트도 공개
    @GetMapping
    public SessionReportResponse get(@PathVariable String eventCode, @PathVariable Long sessionId) {
        return sessionReportService.getReport(eventCode, sessionId);
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204: 회수 완료(재생성 가능해짐)
    public void reset(
            @AuthenticationPrincipal Long userId, @PathVariable String eventCode, @PathVariable Long sessionId) {
        sessionReportService.reset(userId, eventCode, sessionId);
    }
}

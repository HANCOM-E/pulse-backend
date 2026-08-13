package com.hancome.pulse.report;

import com.hancome.pulse.report.dto.ReportPublishRequest;
import com.hancome.pulse.report.dto.ReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리포트 API. 생성·토글은 소유자 인증, 조회는 auth 인식(소유자/게스트).
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/report")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED) // 202: 접수·처리 시작(GENERATING). 완료는 GET 폴링으로 확인.
    public ReportResponse generate(@AuthenticationPrincipal Long userId, @PathVariable String eventCode) {
        return reportService.generate(userId, eventCode);
    }

    // 공개 조회지만 인증되면 소유자 전체 뷰를 준다. principal이 Long이 아니어도(익명) null로 받도록 errorOnInvalidType=false.
    @Operation(security = {})
    @GetMapping
    public ResponseEntity<?> get(
            @AuthenticationPrincipal(errorOnInvalidType = false) Long userId, @PathVariable String eventCode) {
        return ResponseEntity.ok(reportService.getReport(eventCode, userId));
    }

    @PatchMapping
    public ReportResponse toggle(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @Valid @RequestBody ReportPublishRequest req) {
        return reportService.toggle(userId, eventCode, req.isPublic());
    }
}

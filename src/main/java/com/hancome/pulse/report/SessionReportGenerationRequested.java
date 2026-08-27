package com.hancome.pulse.report;

/**
 * 세션 리포트 생성이 요청됐음을 알리는 도메인 이벤트. {@code SessionReportService.generate}가 GENERATING 리포트를 저장한 뒤 발행하고,
 * 커밋 후 {@link SessionReportGenerationWorker}가 비동기로 집계·요약을 채운다. {@link ReportGenerationRequested}의 세션 판.
 */
public record SessionReportGenerationRequested(Long sessionReportId, String eventCode, Long sessionId) {}

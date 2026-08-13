package com.hancome.pulse.report;

/**
 * 리포트 생성이 요청됐음을 알리는 도메인 이벤트. {@code generate}가 {@code GENERATING} 리포트를 저장한 뒤 발행하고,
 * {@link ReportGenerationWorker}가 트랜잭션 커밋 후 비동기로 받아 채운다.
 *
 * @param reportId 방금 저장된 GENERATING 리포트 PK
 * @param eventCode 집계 대상 이벤트 공개 코드
 */
public record ReportGenerationRequested(Long reportId, String eventCode) {}

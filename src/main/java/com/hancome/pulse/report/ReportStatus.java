package com.hancome.pulse.report;

// 리포트 생성 상태. @Enumerated(STRING)으로 저장한다.
// NONE(리포트 행 없음)은 개념적 상태라 DB에 저장하지 않는다 — 행 자체가 "요약 생성" 요청 시점에
// GENERATING으로 처음 생성된다. 그래서 enum에는 실제로 저장되는 값만 둔다.
public enum ReportStatus {
    GENERATING,
    GENERATED,
    FAILED
}

package com.hancome.pulse.event;

public enum SessionStatus {
    ACTIVE,
    CLOSED, // 피드백 마감. 조회는 되지만 신규 소감 제출을 차단한다(생성 시 기본값 — 진행 시점에 소유자가 ACTIVE로 연다).
    DELETED // 소프트 삭제. 물리 삭제 대신 마킹만 남긴다(딸린 소감 유무와 무관하게 삭제 가능).
}

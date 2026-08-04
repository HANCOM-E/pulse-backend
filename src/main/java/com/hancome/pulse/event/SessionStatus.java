package com.hancome.pulse.event;

public enum SessionStatus {
    ACTIVE,
    DELETED // 소프트 삭제. 물리 삭제 대신 마킹만 남긴다(딸린 소감 유무와 무관하게 삭제 가능).
}

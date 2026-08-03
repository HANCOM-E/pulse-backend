package com.hancome.pulse.event;

// 이벤트 상태. @Enumerated(STRING)으로 저장하므로 DB엔 "DRAFT"/"LIVE"/"ENDED" 문자열로 들어간다.
// (자바 상수는 대문자 관례. 기획서의 draft/live/ended와 의미 동일.)
public enum EventStatus {
    DRAFT,
    LIVE,
    ENDED
}

package com.hancome.pulse.feedback;

/** 소감 상태가 바뀐 이벤트(제출·숨김·해제·삭제). SSE 브로드캐스트 트리거로, 바뀐 이벤트 코드만 나른다. */
public record FeedbackChanged(String eventCode) {}

package com.hancome.pulse.event;

/** 세션이 바뀐 이벤트(생성·상태변경·삭제). SSE 브로드캐스트 트리거로, 바뀐 이벤트 코드만 나른다. */
public record SessionChanged(String eventCode) {}

package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Session;
import com.hancome.pulse.event.SessionStatus;

/** 공개 세션 뷰(내부 {@code eventId} 제외). DELETED는 목록에 노출되지 않아 {@code status}는 실질적으로 ACTIVE|CLOSED. */
public record SessionView(Long id, String title, Integer order, SessionStatus status) {
    /**
     * @param session 세션 엔티티
     * @return 내부 식별자를 뺀 공개 뷰
     */
    public static SessionView from(Session session) {
        return new SessionView(session.getId(), session.getTitle(), session.getOrder(), session.getStatus());
    }
}

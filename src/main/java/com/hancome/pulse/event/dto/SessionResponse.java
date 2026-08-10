package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Session;
import com.hancome.pulse.event.SessionStatus;

/** 소유자 응답용 전체 세션 뷰(내부 {@code eventId} 포함). 공개 조회에는 {@link SessionView}를 쓴다. */
public record SessionResponse(Long id, Long eventId, String title, Integer order, SessionStatus status) {
    /**
     * 엔티티 → 전체 뷰 매핑. {@code event}는 지연 로딩이므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param session 세션 엔티티
     * @return 전체 뷰 DTO
     */
    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getEvent().getId(),
                session.getTitle(),
                session.getOrder(),
                session.getStatus());
    }
}

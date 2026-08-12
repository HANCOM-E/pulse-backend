package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 소유자·인증 응답용 전체 뷰(팀 API 명세서의 {@code Event}). 내부 {@code id}·{@code ownerId}를 포함한다.
 *
 * <p>공개 조회에는 {@link EventView}를 쓴다.
 */
public record EventResponse(
        Long id,
        String code,
        String title,
        String description,
        LocalDate eventDate,
        Long ownerId,
        EventStatus status,
        Instant createdAt) {

    /**
     * 엔티티 → 전체 뷰 매핑. {@code owner}는 지연 로딩이므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param e 이벤트 엔티티
     * @return 전체 뷰 DTO
     */
    public static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                e.getEventDate(),
                e.getOwner().getId(),
                e.getStatus(),
                e.getCreatedAt());
    }
}

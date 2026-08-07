package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventStatus;
import java.time.Instant;

/** 공개 이벤트 뷰(내부 {@code id}·{@code ownerId} 제외). 비인증 {@code GET /events/&#123;eventCode&#125;} 응답용. */
public record EventView(String code, String title, String description, EventStatus status, Instant createdAt) {

    /**
     * @param e 이벤트 엔티티
     * @return 내부 식별자를 뺀 공개 뷰
     */
    public static EventView from(Event e) {
        return new EventView(e.getCode(), e.getTitle(), e.getDescription(), e.getStatus(), e.getCreatedAt());
    }
}

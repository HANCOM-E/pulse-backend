package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.EventStatus;
import jakarta.validation.constraints.Size;

/**
 * 이벤트 부분 수정 요청. 모든 필드 optional(보낸 것만 반영). {@code code}·{@code ownerId}·{@code createdAt}은 수정 불가.
 *
 * <p>{@code status}로는 상태 전이(DRAFT→LIVE, LIVE→ENDED)만 허용한다. DRAFT·DELETED로의 전이나 잘못된 순서는 서비스에서
 * {@code INVALID_EVENT_STATE_TRANSITION}으로 막는다(값 자체는 여기서 검증하지 않음).
 */
public record EventUpdateRequest(
        @Size(min = 2, max = 60) String title,
        @Size(max = 500) String description,
        EventStatus status) {}

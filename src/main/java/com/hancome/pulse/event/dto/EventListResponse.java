package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Event;
import java.util.List;

/**
 * 내 이벤트 목록 봉투. 페이지네이션은 v1 미도입이지만 {@code {items}}로 감싸 향후 {@code nextCursor} 등을 비파괴적으로 추가할 seam을
 * 확보한다.
 */
public record EventListResponse(List<EventResponse> items) {

    /**
     * @param events 소유자의 이벤트 목록
     * @return 전체 뷰로 매핑한 목록 봉투
     */
    public static EventListResponse from(List<Event> events) {
        return new EventListResponse(events.stream().map(EventResponse::from).toList());
    }
}

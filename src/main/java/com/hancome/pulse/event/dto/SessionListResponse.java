package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.Session;
import java.util.List;

/** 세션 목록 봉투(페이지네이션 seam). 공개 조회이므로 {@link SessionView}로 담는다(DELETED는 이미 제외되어 들어옴). */
public record SessionListResponse(List<SessionView> items) {
    /**
     * @param sessions 세션 목록(DELETED 제외된 상태로 전달)
     * @return 공개 뷰로 매핑한 목록 봉투
     */
    public static SessionListResponse from(List<Session> sessions) {
        return new SessionListResponse(sessions.stream().map(SessionView::from).toList());
    }
}

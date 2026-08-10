package com.hancome.pulse.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /** 이벤트의 공개 세션 목록: 특정 상태 제외(DELETED 숨김) + {@code order} 오름차순. */
    List<Session> findByEvent_CodeAndStatusNotOrderByOrderAsc(String code, SessionStatus status);
}

package com.hancome.pulse.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** 공개 코드로 이벤트 단건 조회(쓰기·공개 조회 공통 진입점). */
    Optional<Event> findByCode(String code);

    /** 저장 실패가 code 유니크 충돌인지 판별용(사전검사 아님 — 저장 실패 후 원인 구분). */
    boolean existsByCode(String code);

    /** 소유자의 이벤트 목록에서 특정 상태를 제외해 조회(목록에서 DELETED 숨김용). */
    List<Event> findByOwner_IdAndStatusNot(Long ownerId, EventStatus status);
}

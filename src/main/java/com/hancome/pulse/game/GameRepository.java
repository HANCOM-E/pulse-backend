package com.hancome.pulse.game;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

    /** 이벤트의 전체 게임 목록(주최자용): 최근 생성 순. */
    List<Game> findByEvent_CodeOrderByCreatedAtDesc(String eventCode);

    /** 이벤트 스코프로 게임 단건 조회. 다른 이벤트의 게임 id로 접근하는 것을 막는다(경로 정합). */
    Optional<Game> findByIdAndEvent_Code(Long id, String eventCode);

    /** 공개용 "현재 게임": DRAFT 제외한 가장 최근 게임 1개(없으면 empty). */
    Optional<Game> findFirstByEvent_CodeAndStatusNotOrderByCreatedAtDesc(String eventCode, GameStatus status);
}

package com.hancome.pulse.game;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /** 재참가 upsert 조회: 같은 게임에서 같은 clientId의 기존 참가자(있으면 닉네임만 갱신). */
    Optional<Participant> findByGame_IdAndClientId(Long gameId, String clientId);
}

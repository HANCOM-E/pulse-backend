package com.hancome.pulse.game.dto;

import com.hancome.pulse.game.Participant;
import java.time.Instant;

/** 공개 참가자 뷰. 내부 식별자 {@code clientId}는 제외하고 {@code id·nickname·joinedAt}만 노출한다. */
public record ParticipantView(Long id, String nickname, Instant joinedAt) {

    /**
     * @param p 참가자 엔티티
     * @return clientId를 뺀 공개 뷰
     */
    public static ParticipantView from(Participant p) {
        return new ParticipantView(p.getId(), p.getNickname(), p.getJoinedAt());
    }
}

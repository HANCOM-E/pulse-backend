package com.hancome.pulse.game.dto;

import com.hancome.pulse.game.Game;
import com.hancome.pulse.game.GameStatus;
import com.hancome.pulse.game.GameType;
import java.time.Instant;
import java.util.List;

/**
 * 게임 공개 뷰(단건·현재·목록 공통). 상태·참가자·결과(순위)를 담는다.
 *
 * <p>{@code ranking}은 참가자 PK를 등수 순서로 담은 배열(종료 전엔 빈 배열). 참가자는 {@link ParticipantView}라 {@code clientId}가
 * 빠진다. LAZY 컬렉션({@code participants}·{@code ranking})에 접근하므로 트랜잭션 안에서 매핑해야 한다.
 */
public record GameView(
        Long id,
        String title,
        GameType gameType,
        GameStatus status,
        List<ParticipantView> participants,
        List<Long> ranking,
        Instant createdAt) {

    /**
     * @param g 게임 엔티티(참가자·랭킹 LAZY 로딩되므로 트랜잭션 내 호출)
     * @return 공개 뷰
     */
    public static GameView from(Game g) {
        return new GameView(
                g.getId(),
                g.getTitle(),
                g.getGameType(),
                g.getStatus(),
                g.getParticipants().stream().map(ParticipantView::from).toList(),
                List.copyOf(g.getRanking()), // lazy 컬렉션을 트랜잭션 안에서 실체화(직렬화 시점 no-session 방지)
                g.getCreatedAt());
    }
}

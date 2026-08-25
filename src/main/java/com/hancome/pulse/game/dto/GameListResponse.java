package com.hancome.pulse.game.dto;

import com.hancome.pulse.game.Game;
import java.util.List;

/** 게임 목록 봉투(주최자용). {@link GameView}로 담는다(참가자·결과 포함). */
public record GameListResponse(List<GameView> items) {
    /**
     * @param games 게임 목록
     * @return 뷰로 매핑한 목록 봉투
     */
    public static GameListResponse from(List<Game> games) {
        return new GameListResponse(games.stream().map(GameView::from).toList());
    }
}

package com.hancome.pulse.game;

// 미니게임 종류. 현재는 핀볼 레이스만. @Enumerated(STRING)으로 저장하므로 DB엔 문자열로 들어간다.
// (결과 랭킹 배열은 핀볼 전용 스키마 — 다른 타입을 추가하면 결과 표현도 분기해야 한다.)
public enum GameType {
    PINBALL
}

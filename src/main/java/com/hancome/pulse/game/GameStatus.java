package com.hancome.pulse.game;

// 미니게임 상태. DRAFT → OPEN → RUNNING → FINISHED 로만 흐르는 단방향 상태머신(되돌리기·건너뛰기 없음).
// - DRAFT:    주최자가 만든 직후(참가 불가, current 조회에서 제외)
// - OPEN:     참가 접수 중(게스트가 참가/닉네임 갱신 가능)
// - RUNNING:  레이스 진행 중(참가 마감)
// - FINISHED: 종료·결과(ranking) 확정(터미널 — 이후 어떤 전이도 불가)
// @Enumerated(STRING)으로 저장하므로 상수 순서를 바꿔도 기존 행 의미가 어긋나지 않는다.
public enum GameStatus {
    DRAFT,
    OPEN,
    RUNNING,
    FINISHED
}

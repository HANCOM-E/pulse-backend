package com.hancome.pulse.feedback;

public enum Sentiment {
    POS,
    NEU,
    NEG,
    UNKNOWN // 클라이언트 모델 태깅 실패 시. NEU(진짜 중립)와 섞이지 않도록 별도 값
}

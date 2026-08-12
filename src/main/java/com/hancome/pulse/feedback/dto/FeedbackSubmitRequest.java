package com.hancome.pulse.feedback.dto;

import com.hancome.pulse.feedback.Sentiment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 소감 제출 요청(클라이언트 태깅 계약). 감정·독성·키워드는 클라이언트가 태깅해 보내고 서버는 그대로 저장한다.
 *
 * @param sessionId 소감을 달 세션 PK
 * @param text 소감 본문 1~200자
 * @param sentiment 클라 태깅 감정(POS/NEU/NEG/UNKNOWN)
 * @param toxic 클라 태깅 독성 여부
 * @param keywords 키워드 0~5개(각 1~20자, 중복 불가)
 * @param taggerVersion 태깅 모델 버전(필수 — 없으면 거절)
 */
public record FeedbackSubmitRequest(
        @NotNull Long sessionId,
        @NotBlank @Size(min = 1, max = 200) String text,
        @NotNull Sentiment sentiment,
        @NotNull Boolean toxic,
        @NotNull @Size(max = 5) @UniqueElements List<@NotBlank @Size(min = 1, max = 20) String> keywords,
        @NotBlank String taggerVersion) {}

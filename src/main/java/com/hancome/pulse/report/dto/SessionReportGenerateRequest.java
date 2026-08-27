package com.hancome.pulse.report.dto;

import jakarta.validation.constraints.Size;

/**
 * 세션 리포트 생성 요청. 강연자가 프론트에서 발표 자료를 LLM 요약한 결과({@code materialSummary})만 담는다(원본 파일은 서버로 오지 않는다).
 * 선택값 — 자료 없이 피드백만으로 생성할 수도 있다. 길이 상한은 이벤트 리포트 융합 시 프롬프트 토큰 폭증을 막는다.
 */
public record SessionReportGenerateRequest(@Size(max = 2000) String materialSummary) {}

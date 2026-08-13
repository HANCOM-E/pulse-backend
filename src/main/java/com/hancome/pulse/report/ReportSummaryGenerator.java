package com.hancome.pulse.report;

import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import org.springframework.stereotype.Component;

/**
 * 집계 스냅샷으로 리포트 요약문을 생성한다. 지금은 스텁(placeholder)만 반환하고, 실제 LLM(OpenRouter) 호출은 이후에 이 메서드 본문만
 * 교체한다 — 나머지 리포트 생성 파이프라인은 이 seam 하나에만 의존한다.
 */
@Component
public class ReportSummaryGenerator {

    // ponytail: LLM 연동은 이 한 곳만 교체하면 된다. 실제 호출 시 RestClient로 아래 엔드포인트에 POST + API 키(env)로.
    //   단일 구현이라 인터페이스는 두지 않음(YAGNI). 두 번째 제공자가 생기면 그때 추상화.
    static final String OPENROUTER_URL = "https://openrouter.ai/api/v1";

    /**
     * 집계 스냅샷을 근거로 요약문을 만든다.
     *
     * @param snapshot 이벤트 전체 집계(감정 분포·키워드 등)
     * @return 요약 텍스트(현재는 스텁)
     */
    public String summarize(FeedbackSnapshot snapshot) {
        // TODO(LLM): OPENROUTER_URL 로 snapshot을 프롬프트에 담아 요청하고 응답 요약을 반환.
        return "(요약 생성 예정)";
    }
}

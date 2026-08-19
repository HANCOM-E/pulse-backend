package com.hancome.pulse.report;

import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import com.hancome.pulse.feedback.dto.FeedbackView;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 집계 스냅샷의 소감 원문을 근거로 OpenRouter LLM에 이벤트 전체 요약문(단일 텍스트)을 요청한다. 세션별 분리는 하지 않는다(MVP) — 필요해지면 이 클래스만
 * 세션 루프 + 구조화 출력으로 확장하면 되고, 나머지 리포트 파이프라인은 이 seam 하나에만 의존한다.
 *
 * <p>실패(HTTP 4xx/5xx·타임아웃·빈 응답)는 모두 RuntimeException으로 전파해 {@link ReportGenerationWorker}가 리포트를 {@code
 * FAILED}로 확정하게 한다. 자동 재시도는 하지 않는다(사용자가 재생성으로 재시도).
 */
@Component
public class ReportSummaryGenerator {

    // ponytail: 단일 제공자(OpenRouter)라 인터페이스는 두지 않음. 두 번째 제공자가 생기면 그때 추상화(YAGNI).
    static final String OPENROUTER_URL = "https://openrouter.ai/api/v1";

    // %d에 실제 소감 개수를 박아 "이게 전부"임을 명시 — 입력이 빈약할 때 모델이 가짜 후기를 창작하는 것을 막는다.
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            너는 행사 참가자 소감을 요약하는 어시스턴트야.
            아래는 실제로 수집된 소감 %d건의 전부야. 이것만 근거로 삼아.
            없는 소감을 새로 지어내지 말고, 소감을 그대로 나열하지도 마.
            전체를 3~5문장 한국어 요약으로만 작성해. 만족도·자주 나온 의견·개선점을 균형 있게 담아.
            """;

    private final RestClient client;
    private final String model;

    public ReportSummaryGenerator(RestClient openRouterClient, @Value("${openrouter.model}") String model) {
        this.client = openRouterClient;
        this.model = model;
    }

    /**
     * 집계 스냅샷을 근거로 요약문을 만든다.
     *
     * @param snapshot 이벤트 전체 집계(소감 원문 포함)
     * @return 요약 텍스트
     * @throws RuntimeException LLM 호출 실패·타임아웃·빈 응답 시(→ 워커가 FAILED 처리)
     */
    public String summarize(FeedbackSnapshot snapshot) {
        List<FeedbackView> feedbacks = snapshot.recentFeedbacks();
        if (feedbacks == null || feedbacks.isEmpty()) {
            // 소감이 없으면 LLM을 부르지 않는다 — 빈 입력이면 없는 내용을 지어내므로.
            return "수집된 소감이 없어 요약할 내용이 없습니다.";
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(feedbacks.size());
        ChatRequest request = new ChatRequest(
                model,
                List.of(new Message("system", systemPrompt), new Message("user", buildUserPrompt(feedbacks))),
                // temperature=0: 요약은 창작이 아니라 근거 압축 — 결정적으로 뽑아 할루시네이션을 억제한다.
                0.0);

        ChatResponse response = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        String summary = response == null ? null : response.firstContent();
        if (!StringUtils.hasText(summary)) {
            throw new IllegalStateException("LLM이 빈 요약을 반환했습니다");
        }
        return summary.strip();
    }

    private String buildUserPrompt(List<FeedbackView> feedbacks) {
        // ponytail: 스냅샷 recentFeedbacks(최신 50) 기준. 이벤트가 50개를 크게 넘고 요약 품질이 떨어지면 전량 조회 쿼리를 추가.
        return feedbacks.stream().map(f -> "- " + f.text()).collect(Collectors.joining("\n"));
    }

    // OpenRouter Chat Completions 요청/응답의 최소 스키마(필요한 필드만 — 나머지는 Jackson이 무시).
    record ChatRequest(String model, List<Message> messages, double temperature) {}

    record Message(String role, String content) {}

    record ChatResponse(List<Choice> choices) {
        String firstContent() {
            if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
                return null;
            }
            return choices.get(0).message().content();
        }
    }

    record Choice(Message message) {}
}

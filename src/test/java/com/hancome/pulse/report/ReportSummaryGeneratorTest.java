package com.hancome.pulse.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hancome.pulse.feedback.Sentiment;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import com.hancome.pulse.feedback.dto.FeedbackView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** LLM 응답 파싱·빈 응답 가드·빈 입력 단축을 실제 HTTP 없이 검증(MockRestServiceServer). */
class ReportSummaryGeneratorTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl(ReportSummaryGenerator.OPENROUTER_URL);
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final ReportSummaryGenerator generator = new ReportSummaryGenerator(builder.build(), "test-model");

    @Test
    void 정상_응답이면_요약_반환() {
        server.expect(requestTo(ReportSummaryGenerator.OPENROUTER_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"전반적으로 만족스러웠습니다.\"}}]}", MediaType.APPLICATION_JSON));

        String summary = generator.summarize(snapshotWith(view("좋았어요")));

        assertThat(summary).isEqualTo("전반적으로 만족스러웠습니다.");
        server.verify();
    }

    @Test
    void 빈_응답이면_예외() {
        server.expect(requestTo(ReportSummaryGenerator.OPENROUTER_URL + "/chat/completions"))
                .andRespond(
                        withSuccess("{\"choices\":[{\"message\":{\"content\":\"\"}}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> generator.summarize(snapshotWith(view("좋았어요"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 소감_없으면_LLM_호출없이_기본문구() {
        // server에 아무 기대도 걸지 않음 → LLM을 부르면 verify()가 실패.
        String summary = generator.summarize(snapshotWith());

        assertThat(summary).contains("소감이 없");
        server.verify();
    }

    private FeedbackSnapshot snapshotWith(FeedbackView... views) {
        return new FeedbackSnapshot(null, 0, List.of(), List.of(views));
    }

    private FeedbackView view(String text) {
        return new FeedbackView(1L, 1L, text, Sentiment.NEU, List.of(), Instant.now());
    }
}

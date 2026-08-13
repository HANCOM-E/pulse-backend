package com.hancome.pulse.report;

import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트를 실제로 채우는 트랜잭션 경계. {@link ReportGenerationWorker}와 분리한 이유는 (1) 성공/실패를 각각 독립 트랜잭션으로 다루기
 * 위해서고 (2) 프록시를 거쳐야 {@code @Transactional}이 실제 적용되기 때문이다(같은 빈 자기호출이면 무시).
 *
 * <p>{@link #fill}이 도중에 예외로 롤백되면 리포트는 {@code GENERATING}으로 남는데, 그 상태로는 재생성이 막히므로({@code
 * REPORT_ALREADY_EXISTS}) 워커가 {@link #markFailed}를 별도 트랜잭션({@code REQUIRES_NEW})으로 호출해 {@code FAILED}로
 * 확정한다 — 실패 트랜잭션에 얹으면 rollback-only라 저장 자체가 실패하기 때문이다.
 */
@Component
public class ReportFiller {
    private final ReportRepository reportRepository;
    private final FeedbackService feedbackService;
    private final ReportSummaryGenerator summaryGenerator;

    public ReportFiller(
            ReportRepository reportRepository,
            FeedbackService feedbackService,
            ReportSummaryGenerator summaryGenerator) {
        this.reportRepository = reportRepository;
        this.feedbackService = feedbackService;
        this.summaryGenerator = summaryGenerator;
    }

    /**
     * 집계·요약을 채워 {@code GENERATED}로 전이한다(완료 시각 기록). 집계는 {@code FeedbackService.getSnapshot}(이벤트 전체)을
     * 재사용한다. 예외가 나면 이 트랜잭션은 롤백되고 리포트는 {@code GENERATING}으로 남는다(호출자가 {@link #markFailed}로 마무리).
     *
     * @param reportId 채울 리포트 PK
     * @param eventCode 집계 대상 이벤트 코드
     */
    @Transactional
    public void fill(Long reportId, String eventCode) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) return; // 요청 사이에 사라졌으면(경합) 조용히 종료
        FeedbackSnapshot snapshot = feedbackService.getSnapshot(eventCode, null);
        report.setSentimentBreakdown(snapshot.sentimentBreakdown());
        report.setUnclassifiedCount(snapshot.unclassifiedCount());
        report.setTopKeywords(snapshot.topKeywords());
        report.setSummaryText(summaryGenerator.summarize(snapshot));
        report.setStatus(ReportStatus.GENERATED);
        report.setGeneratedAt(Instant.now());
    }

    /**
     * 리포트를 {@code FAILED}로 확정한다. 실패 트랜잭션과 독립적({@code REQUIRES_NEW})이라, fill이 롤백된 뒤에도 상태 저장이 보장된다.
     *
     * @param reportId 대상 리포트 PK
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long reportId) {
        reportRepository.findById(reportId).ifPresent(r -> r.setStatus(ReportStatus.FAILED));
    }
}

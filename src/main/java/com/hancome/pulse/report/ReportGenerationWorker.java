package com.hancome.pulse.report;

import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * GENERATING 리포트를 집계·요약으로 채워 GENERATED로 마무리하는 비동기 워커.
 *
 * <p>{@code ReportService}와 별도 빈이라 self-invocation 프록시 함정을 피한다({@code @Async}·{@code @Transactional}이
 * 프록시를 거쳐 실제로 적용됨). {@link TransactionalEventListener}의 {@code AFTER_COMMIT}이라, generate가 GENERATING을
 * 커밋해 다른 트랜잭션에서도 조회 가능해진 뒤에야 실행된다({@code REQUIRES_NEW}로 새 트랜잭션). LLM이 스텁이라 즉시 끝나지만 구조는
 * 비동기라, 나중에 실제 LLM(느린 호출)로 바꿔도 요청 스레드를 붙잡지 않는다.
 */
@Component
public class ReportGenerationWorker {
    private final ReportRepository reportRepository;
    private final FeedbackService feedbackService;
    private final ReportSummaryGenerator summaryGenerator;

    public ReportGenerationWorker(
            ReportRepository reportRepository,
            FeedbackService feedbackService,
            ReportSummaryGenerator summaryGenerator) {
        this.reportRepository = reportRepository;
        this.feedbackService = feedbackService;
        this.summaryGenerator = summaryGenerator;
    }

    /**
     * 커밋 후 비동기로 리포트를 채운다. 예외가 나면 {@code FAILED}로 남겨 재생성이 가능하게 한다.
     *
     * @param event 생성 요청 이벤트(리포트 PK·이벤트 코드)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReportRequested(ReportGenerationRequested event) {
        fill(event.reportId(), event.eventCode());
    }

    /**
     * 집계·요약을 채워 GENERATED로 전이한다(테스트에서 동기로 직접 호출 가능). 집계는 {@code
     * FeedbackService.getSnapshot}(이벤트 전체)을 재사용한다.
     *
     * @param reportId 채울 리포트 PK
     * @param eventCode 집계 대상 이벤트 코드
     */
    @Transactional
    public void fill(Long reportId, String eventCode) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) return; // 요청 사이에 사라졌으면(경합) 조용히 종료
        try {
            FeedbackSnapshot snapshot = feedbackService.getSnapshot(eventCode, null);
            report.setSentimentBreakdown(snapshot.sentimentBreakdown());
            report.setUnclassifiedCount(snapshot.unclassifiedCount());
            report.setTopKeywords(snapshot.topKeywords());
            report.setSummaryText(summaryGenerator.summarize(snapshot));
            report.setStatus(ReportStatus.GENERATED);
        } catch (RuntimeException e) {
            report.setStatus(ReportStatus.FAILED); // 실패해도 행은 남겨 재생성(NONE/FAILED 조건) 가능
        }
        reportRepository.save(report);
    }
}

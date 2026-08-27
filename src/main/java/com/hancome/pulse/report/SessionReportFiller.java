package com.hancome.pulse.report;

import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 리포트를 실제로 채우는 트랜잭션 경계. {@link ReportFiller}의 세션 판 — 집계는 세션 스코프
 * {@code FeedbackService.getSnapshot(eventCode, sessionId)}로, 요약은 그 세션 피드백 + 저장된 자료요약을 함께 근거로 만든다.
 */
@Component
public class SessionReportFiller {
    private final SessionReportRepository sessionReportRepository;
    private final FeedbackService feedbackService;
    private final ReportSummaryGenerator summaryGenerator;

    public SessionReportFiller(
            SessionReportRepository sessionReportRepository,
            FeedbackService feedbackService,
            ReportSummaryGenerator summaryGenerator) {
        this.sessionReportRepository = sessionReportRepository;
        this.feedbackService = feedbackService;
        this.summaryGenerator = summaryGenerator;
    }

    /**
     * 세션 집계·요약을 채워 {@code GENERATED}로 전이한다(완료 시각 기록). 예외가 나면 롤백되고 {@code GENERATING}으로 남아,
     * 호출자({@link SessionReportGenerationWorker})가 {@link #markFailed}로 마무리한다.
     *
     * @param sessionReportId 채울 세션 리포트 PK
     * @param eventCode 집계 대상 이벤트 코드
     * @param sessionId 집계 대상 세션 PK
     */
    @Transactional
    public void fill(Long sessionReportId, String eventCode, Long sessionId) {
        SessionReport report = sessionReportRepository.findById(sessionReportId).orElse(null);
        if (report == null) return; // 요청 사이에 사라졌으면(경합·리셋) 조용히 종료
        FeedbackSnapshot snapshot = feedbackService.getSnapshot(eventCode, sessionId);
        report.setSentimentBreakdown(snapshot.sentimentBreakdown());
        report.setUnclassifiedCount(snapshot.unclassifiedCount());
        report.setTopKeywords(snapshot.topKeywords());
        report.setSummaryText(summaryGenerator.summarize(snapshot, report.getMaterialSummary()));
        report.setStatus(ReportStatus.GENERATED);
        report.setGeneratedAt(Instant.now());
    }

    /**
     * 세션 리포트를 {@code FAILED}로 확정한다(별도 트랜잭션이라 fill 롤백 후에도 저장 보장).
     *
     * @param sessionReportId 대상 세션 리포트 PK
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long sessionReportId) {
        sessionReportRepository.findById(sessionReportId).ifPresent(r -> r.setStatus(ReportStatus.FAILED));
    }
}

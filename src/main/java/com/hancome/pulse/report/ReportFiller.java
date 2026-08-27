package com.hancome.pulse.report;

import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final SessionReportRepository sessionReportRepository;
    private final FeedbackService feedbackService;
    private final ReportSummaryGenerator summaryGenerator;

    public ReportFiller(
            ReportRepository reportRepository,
            SessionReportRepository sessionReportRepository,
            FeedbackService feedbackService,
            ReportSummaryGenerator summaryGenerator) {
        this.reportRepository = reportRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.feedbackService = feedbackService;
        this.summaryGenerator = summaryGenerator;
    }

    /**
     * 집계·요약을 채워 {@code GENERATED}로 전이한다(완료 시각 기록). 집계는 {@code FeedbackService.getSnapshot}(이벤트 전체)을
     * 재사용한다. 예외가 나면 이 트랜잭션은 롤백되고 리포트는 {@code GENERATING}으로 남는다(호출자가 {@link #markFailed}로 마무리).
     *
     * <p>ponytail: 이 트랜잭션이 {@code summarize}(OpenRouter 블로킹 호출, read-timeout까지)를 감싸고 있어 그동안 Hikari 커넥션
     * 하나를 붙잡는다. {@code @Async} 실행기가 기본 8스레드로 유계라 동시 점유도 8개로 묶이지만, 리포트 생성이 몰리면 풀(기본 10)이 마를 수 있다.
     * 저트래픽 MVP에선 무해 — 리포트 부하가 커지면 (a)읽기 트랜잭션에서 snapshot 확보 → (b)트랜잭션 밖 summarize → (c)짧은 쓰기 트랜잭션
     * 저장, 3단계로 쪼갠다.
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
        // 이벤트 요약엔 전 세션 강연자 자료요약도 근거로 얹는다. 없으면 빈 문자열 → summarize가 소감-only 경로로 처리.
        report.setSummaryText(summaryGenerator.summarize(snapshot, gatherSessionMaterials(eventCode)));
        report.setStatus(ReportStatus.GENERATED);
        report.setGeneratedAt(Instant.now());
    }

    /**
     * 이벤트에 속한 완료된 세션 리포트들의 자료요약을 모아 불릿 문자열로 합친다(이벤트 요약 융합용). 자료가 하나도 없으면 빈 문자열.
     *
     * <p>ponytail: 세션 수 × 자료요약 길이만큼 프롬프트가 커진다. 자료요약은 생성 시 @Size(2000)로 캡되고 세션 수도 소규모라 무해 —
     * 세션이 수십 개로 커지면 이 문자열을 한 번 더 압축(요약의 요약)해서 넘긴다.
     *
     * @param eventCode 이벤트 공개 코드
     * @return "- 요약\n- 요약" 형태(없으면 빈 문자열)
     */
    private String gatherSessionMaterials(String eventCode) {
        return sessionReportRepository.findBySession_Event_Code(eventCode).stream()
                .filter(r -> r.getStatus() == ReportStatus.GENERATED)
                .map(SessionReport::getMaterialSummary)
                .filter(StringUtils::hasText)
                .map(m -> "- " + m.strip())
                .collect(Collectors.joining("\n"));
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

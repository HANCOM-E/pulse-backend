package com.hancome.pulse.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 비동기로 세션 리포트를 채우는 워커. {@link ReportGenerationWorker}의 세션 판 — 트랜잭션 작업은 {@link SessionReportFiller}에
 * 위임하고, 성공({@code fill})과 실패({@code markFailed})를 각각 독립 트랜잭션으로 처리한다.
 */
@Component
public class SessionReportGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(SessionReportGenerationWorker.class);
    private final SessionReportFiller filler;

    public SessionReportGenerationWorker(SessionReportFiller filler) {
        this.filler = filler;
    }

    /**
     * 커밋 후 비동기로 세션 리포트를 채운다. 실패하면 로깅하고 {@code FAILED}로 확정해(별도 트랜잭션) 재생성이 가능하게 한다.
     *
     * @param event 생성 요청 이벤트(세션 리포트 PK·이벤트 코드·세션 PK)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionReportRequested(SessionReportGenerationRequested event) {
        try {
            filler.fill(event.sessionReportId(), event.eventCode(), event.sessionId());
        } catch (RuntimeException e) {
            log.error("세션 리포트 생성 실패 sessionReportId={}", event.sessionReportId(), e);
            filler.markFailed(event.sessionReportId());
        }
    }
}

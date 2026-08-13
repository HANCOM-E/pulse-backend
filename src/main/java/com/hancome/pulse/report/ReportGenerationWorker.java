package com.hancome.pulse.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 비동기로 리포트를 채우는 워커. 트랜잭션 작업은 {@link ReportFiller}에 위임한다.
 *
 * <p>{@link TransactionalEventListener}의 {@code AFTER_COMMIT}이라 generate가 GENERATING을 커밋해 다른 트랜잭션에서도 조회
 * 가능해진 뒤 실행된다. LLM이 스텁이라 즉시 끝나지만 구조는 비동기라, 나중에 실제 LLM(느린 호출)로 바꿔도 요청 스레드를 붙잡지 않는다. 이 리스너
 * 자체엔 트랜잭션을 두지 않고, 성공({@code fill})과 실패({@code markFailed})를 각각 독립 트랜잭션으로 처리한다.
 */
@Component
public class ReportGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationWorker.class);
    private final ReportFiller filler;

    public ReportGenerationWorker(ReportFiller filler) {
        this.filler = filler;
    }

    /**
     * 커밋 후 비동기로 리포트를 채운다. 실패하면 예외를 로깅하고 리포트를 {@code FAILED}로 확정해(별도 트랜잭션) 재생성이 가능하게 한다.
     *
     * @param event 생성 요청 이벤트(리포트 PK·이벤트 코드)
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportRequested(ReportGenerationRequested event) {
        try {
            filler.fill(event.reportId(), event.eventCode());
        } catch (RuntimeException e) {
            // @Async라 예외가 호출자에게 전달되지 않으므로 여기서 로깅해야 원인이 남는다.
            log.error("리포트 생성 실패 reportId={}", event.reportId(), e);
            filler.markFailed(event.reportId());
        }
    }
}

package com.hancome.pulse.feedback;

import com.hancome.pulse.event.SessionChanged;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소감·세션 변경 이벤트를 커밋 후 비동기로 받아 해당 SSE 채널에 브로드캐스트한다.
 *
 * <p>{@code AFTER_COMMIT}이라 저장·상태변경이 커밋돼 다른 트랜잭션에서도 보이는 뒤에 스냅샷을 계산한다. {@code @Async}로 요청
 * 스레드에서 분리해, 느린 SSE 클라가 원 요청(소감 제출·세션 변경) 응답을 붙잡지 않게 한다({@code ReportGenerationWorker}와 동형).
 */
@Component
public class SseBroadcastListener {
    private final SseHub sseHub;

    public SseBroadcastListener(SseHub sseHub) {
        this.sseHub = sseHub;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackChanged(FeedbackChanged event) {
        sseHub.broadcast(SseHub.feedbackChannel(event.eventCode()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionChanged(SessionChanged event) {
        sseHub.broadcast(SseHub.sessionsChannel(event.eventCode()));
    }
}

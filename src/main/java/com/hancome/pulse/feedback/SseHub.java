package com.hancome.pulse.feedback;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 변경을 구독자에게 SSE로 push하는 인메모리 허브. 채널(예: {@code feedback:CODE}, {@code sessions:CODE}) 단위로 구독하고,
 * 그 채널이 broadcast되면 각 구독자가 자기 스냅샷을 계산해 내려받는다. 연결 시에도 현재 스냅샷을 한 번 보낸다. FE는 받은 JSON을 그대로
 * 렌더한다(refetch 불필요).
 *
 * <p>구독자는 자기 payload 계산법을 {@link Supplier}로 들고 오므로, 허브는 채널 문자열만 알면 되고 어떤 도메인의 스냅샷인지 몰라도 된다.
 *
 * <p>ponytail: 단일 인스턴스 인메모리 한정 — 기존 레이트리밋({@code FeedbackService.rateLog})과 같은 계열. 인스턴스가 2대
 * 이상이 되면 {@link #broadcast}를 부르는 경로를 Redis/Postgres NOTIFY 구독으로 바꾸면 된다(발행부는 그대로).
 */
@Component
public class SseHub {
    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    // 30분: 죽은 연결 상한. Vercel이 먼저 끊으면 EventSource가 재연결하고, 재연결 시 현재 스냅샷을 다시 받는다.
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /** feedback:CODE — 소감 제출·모더레이션 변경 채널(공개 집계·관리자 큐 구독). */
    public static String feedbackChannel(String eventCode) {
        return "feedback:" + eventCode;
    }

    /** sessions:CODE — 세션 생성·상태변경·삭제 채널(공개 세션목록 구독). */
    public static String sessionsChannel(String eventCode) {
        return "sessions:" + eventCode;
    }

    private record Subscriber(SseEmitter emitter, Supplier<Object> payload) {}

    private final Map<String, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /**
     * 채널을 구독한다. 즉시 현재 스냅샷을 1건 보내고, 이후 {@link #broadcast}마다 갱신 스냅샷을 받는다.
     *
     * @param channel 구독 채널
     * @param payload 이 구독자에게 보낼 스냅샷을 계산하는 함수
     * @return 등록된 에미터
     */
    public SseEmitter subscribe(String channel, Supplier<Object> payload) {
        // 초기 스냅샷을 먼저 계산한다. 잘못된 이벤트 등 도메인 예외는 여기서 나가 컨트롤러가 4xx로 응답한다(죽은 스트림 안 염).
        Object initial = payload.get();

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Subscriber sub = new Subscriber(emitter, payload);
        subscribers.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(sub);

        emitter.onCompletion(() -> remove(channel, sub));
        emitter.onTimeout(() -> remove(channel, sub));
        emitter.onError(e -> remove(channel, sub));

        try {
            emitter.send(SseEmitter.event().name("snapshot").data(initial, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            remove(channel, sub); // 초기 전송 직전 이미 끊김 → 등록 취소
        }
        return emitter;
    }

    /**
     * 채널 구독자 전원에게 각자의 최신 스냅샷을 push한다. 커밋 후 리스너가 호출한다(팬아웃 seam).
     *
     * @param channel broadcast할 채널
     */
    public void broadcast(String channel) {
        Set<Subscriber> subs = subscribers.get(channel);
        if (subs == null) {
            return;
        }
        for (Subscriber sub : subs) {
            if (!send(sub)) {
                remove(channel, sub);
            }
        }
    }

    /** 25초마다 코멘트 핑 — 프록시 idle 차단 + 죽은 연결 청소. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        subscribers.forEach((channel, subs) -> {
            for (Subscriber sub : subs) {
                try {
                    sub.emitter().send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(channel, sub);
                }
            }
        });
    }

    // broadcast 전용: 한 구독자의 전송·페이로드 계산 실패가 나머지 구독자를 막지 않게 넓게 잡고, 실패한 구독자는 호출부가 제거한다.
    private boolean send(Subscriber sub) {
        try {
            sub.emitter()
                    .send(SseEmitter.event().name("snapshot").data(sub.payload().get(), MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            // 끊긴 연결이 대부분이라 debug로만 남긴다. 실패한 구독자는 호출부가 제거한다.
            log.debug("[SSE] dropping subscriber after send failure: {}", e.toString());
            return false;
        }
    }

    // 빈 채널 버킷은 남겨둔다: 지우면 동시 subscribe와 레이스가 나고, 개수가 적어 무해하다(ponytail).
    private void remove(String channel, Subscriber sub) {
        Set<Subscriber> subs = subscribers.get(channel);
        if (subs != null) {
            subs.remove(sub);
        }
    }
}

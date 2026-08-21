package com.hancome.pulse.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SseHubTest {
    private final SseHub hub = new SseHub();

    @Test
    void sendsSnapshotOnSubscribeThenOnEachBroadcast() {
        // 구독자의 payload 호출 횟수로 "연결 즉시 1건 + broadcast마다 재전송"을 확인한다.
        AtomicInteger calls = new AtomicInteger();
        assertNotNull(hub.subscribe("feedback:E1", calls::incrementAndGet));
        assertEquals(1, calls.get(), "연결 즉시 현재 스냅샷 1건");

        hub.broadcast("feedback:E1");
        assertEquals(2, calls.get(), "변경 시 갱신 스냅샷");

        hub.broadcast("sessions:E1"); // 다른 채널 → 이 구독자엔 영향 없음
        assertEquals(2, calls.get());
    }

    @Test
    void broadcastToUnknownChannelIsNoop() {
        hub.broadcast("nobody:here"); // 구독자 없어도 예외 없이 통과
    }
}

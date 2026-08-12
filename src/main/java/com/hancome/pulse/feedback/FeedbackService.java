package com.hancome.pulse.feedback;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.*;
import com.hancome.pulse.feedback.dto.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소감 제출·집계 도메인 로직. 제출은 공개(비인증)지만 게이트·레이트리밋으로 보호한다.
 *
 * <p>레포 3개를 주입해 둔다 — 게이트에 event/session, 저장·집계에 feedback. 집계 쿼리는 {@link FeedbackRepository}에 추가한다.
 */
@Service
public class FeedbackService {
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            FeedbackRepository feedbackRepository) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    private final Map<String, Deque<Instant>> rateLog = new ConcurrentHashMap<>();
    private static final int LIMIT = 3;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private Session loadSubmittableSession(String eventCode, Long sessionId) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        switch (event.getStatus()) {
            case DRAFT, ENDED -> throw new ApiException(ErrorCode.EVENT_NOT_LIVE);
            case DELETED -> throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
            default -> {} // LIVE 통과
        }
        Session session =
                sessionRepository.findById(sessionId).orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getEvent().getId().equals(event.getId())) throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        switch (session.getStatus()) {
            case CLOSED -> throw new ApiException(ErrorCode.SESSION_CLOSED);
            case DELETED -> throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private void checkRateLimit(Long sessionId, String clientId) {
        String key = sessionId + ":" + clientId;
        Deque<Instant> log = rateLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            Instant cutoff = Instant.now().minus(WINDOW); // 60초 전 경계선 게산

            while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
                log.pollFirst();
            }

            if (log.size() >= LIMIT) throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED);

            log.addLast(Instant.now());
        }
    }
    /**
     * 소감을 제출한다. 처리 순서: (1) 게이트 → (2) 레이트리밋 → (3) 저장.
     *
     * @param eventCode 소감을 받는 이벤트 공개 코드(URL)
     * @param req 제출 본문(클라 태깅 포함)
     * @param clientId 레이트리밋 키(컨트롤러가 X-Client-Id 헤더, 없으면 IP를 넘김)
     * @return 접수된 소감 공개 뷰
     * @throws com.hancome.pulse.common.ApiException 게이트/레이트리밋 위반 시 해당 코드
     */
    @Transactional
    public FeedbackView submit(String eventCode, FeedbackSubmitRequest req, String clientId) {
        Session session = loadSubmittableSession(eventCode, req.sessionId());
        checkRateLimit(session.getId(), clientId);

        Feedback feedback =
                new Feedback(session, req.text(), req.sentiment(), req.toxic(), req.taggerVersion(), req.keywords());

        feedbackRepository.save(feedback);

        return FeedbackView.from(feedback);
    }

    /**
     * 실시간 집계 스냅샷을 만든다({@code VISIBLE} 소감만).
     *
     * @param eventCode 이벤트 공개 코드
     * @param sessionId 집계 대상 세션(선택)
     */
    @Transactional(readOnly = true)
    public FeedbackSnapshot getSnapshot(String eventCode, Long sessionId) {
        List<Object[]> rowList = feedbackRepository.countBySentiment(eventCode, sessionId, FeedbackStatus.VISIBLE);
        int pos = 0, neu = 0, neg = 0, unknown = 0;
        for (Object[] row : rowList) {
            Sentiment s = (Sentiment) row[0];
            int cnt = ((Long) row[1]).intValue();
            switch (s) {
                case POS -> pos = cnt;
                case NEU -> neu = cnt;
                case NEG -> neg = cnt;
                case UNKNOWN -> unknown = cnt;
            }
        }
        SentimentBreakdown sentimentBreakdown = new SentimentBreakdown(pos, neu, neg);
        List<KeywordCount> topKeywords =
                feedbackRepository.topKeywords(eventCode, sessionId, FeedbackStatus.VISIBLE, PageRequest.of(0, 10));
        List<FeedbackView> recent =
                feedbackRepository
                        .recentFeedbacks(eventCode, sessionId, FeedbackStatus.VISIBLE, PageRequest.of(0, 50))
                        .stream()
                        .map(FeedbackView::from)
                        .toList();

        return new FeedbackSnapshot(sentimentBreakdown, unknown, topKeywords, recent);
    }
}

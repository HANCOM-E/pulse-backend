package com.hancome.pulse.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.auth.UserRepository;
import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventRepository;
import com.hancome.pulse.event.EventStatus;
import com.hancome.pulse.event.Session;
import com.hancome.pulse.event.SessionRepository;
import com.hancome.pulse.event.SessionStatus;
import com.hancome.pulse.feedback.dto.FeedbackSnapshot;
import com.hancome.pulse.feedback.dto.FeedbackSubmitRequest;
import com.hancome.pulse.feedback.dto.FeedbackView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

/**
 * FeedbackService 통합 테스트. 실제 소감을 넣고 getSnapshot/submit을 돌려 집계·게이트·레이트리밋이 진짜 동작하는지 검증한다(부팅
 * 통과만으로는 안 드러나는 실행 시 버그를 잡는 게 목적).
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FeedbackServiceTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final TestEntityManager em;
    private FeedbackService feedbackService;

    FeedbackServiceTest(
            UserRepository userRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            FeedbackRepository feedbackRepository,
            TestEntityManager em) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(eventRepository, sessionRepository, feedbackRepository);
    }

    private Event persistEvent(String code, EventStatus status) {
        User owner = userRepository.save(new User(code + "@pulse.dev", "hashed-pw"));
        Event event = new Event(code, "이벤트", null, LocalDate.of(2026, 8, 15), owner);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private Session persistSession(Event event, SessionStatus status) {
        Session session = new Session(event, "세션", 1);
        session.setStatus(status);
        return sessionRepository.save(session);
    }

    private void saveFeedback(Session session, Sentiment sentiment, List<String> keywords, FeedbackStatus status) {
        Feedback f = new Feedback(session, "소감", sentiment, false, "kobert-v1", keywords);
        f.setStatus(status);
        feedbackRepository.save(f);
    }

    // ===================== getSnapshot 집계 =====================

    @Test
    void 감정분포와_미분류가_VISIBLE만_집계된다() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        saveFeedback(session, Sentiment.POS, List.of("발표속도"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.POS, List.of("내용"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.NEG, List.of("발표속도"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.UNKNOWN, List.of(), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.POS, List.of("숨김"), FeedbackStatus.HIDDEN); // 집계 제외
        em.flush();
        em.clear();

        // when
        FeedbackSnapshot snap = feedbackService.getSnapshot("EVT-A", session.getId());

        // then
        assertThat(snap.sentimentBreakdown().POS()).isEqualTo(2);
        assertThat(snap.sentimentBreakdown().NEU()).isZero();
        assertThat(snap.sentimentBreakdown().NEG()).isEqualTo(1);
        assertThat(snap.unclassifiedCount()).isEqualTo(1);
    }

    @Test
    void topKeywords가_빈도순으로_반환된다() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        saveFeedback(session, Sentiment.POS, List.of("발표속도", "내용"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.NEU, List.of("발표속도"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.NEG, List.of("발표속도"), FeedbackStatus.VISIBLE);
        em.flush();
        em.clear();

        // when
        FeedbackSnapshot snap = feedbackService.getSnapshot("EVT-A", session.getId());

        // then
        assertThat(snap.topKeywords()).extracting(kc -> kc.keyword()).containsExactly("발표속도", "내용");
        assertThat(snap.topKeywords().get(0).count()).isEqualTo(3);
        assertThat(snap.topKeywords().get(1).count()).isEqualTo(1);
    }

    @Test
    void sessionId가_null이면_이벤트_전체가_집계된다() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session s1 = persistSession(event, SessionStatus.ACTIVE);
        Session s2 = persistSession(event, SessionStatus.ACTIVE);
        saveFeedback(s1, Sentiment.POS, List.of("a"), FeedbackStatus.VISIBLE);
        saveFeedback(s2, Sentiment.POS, List.of("a"), FeedbackStatus.VISIBLE);
        em.flush();
        em.clear();

        // when
        FeedbackSnapshot whole = feedbackService.getSnapshot("EVT-A", null);
        FeedbackSnapshot onlyS1 = feedbackService.getSnapshot("EVT-A", s1.getId());

        // then
        assertThat(whole.sentimentBreakdown().POS()).isEqualTo(2); // 두 세션 합산
        assertThat(onlyS1.sentimentBreakdown().POS()).isEqualTo(1); // 한 세션만
    }

    @Test
    void recentFeedbacks는_VISIBLE만_최신순으로_담긴다() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        saveFeedback(session, Sentiment.POS, List.of("a"), FeedbackStatus.VISIBLE);
        saveFeedback(session, Sentiment.NEG, List.of("b"), FeedbackStatus.DELETED); // 제외
        em.flush();
        em.clear();

        // when
        FeedbackSnapshot snap = feedbackService.getSnapshot("EVT-A", session.getId());

        // then
        assertThat(snap.recentFeedbacks()).extracting(FeedbackView::sentiment).containsExactly(Sentiment.POS);
    }

    // ===================== submit 게이트 =====================

    @Test
    void 소감제출이_성공하면_저장되고_뷰를_반환한다() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        em.flush();
        em.clear();
        FeedbackSubmitRequest req =
                new FeedbackSubmitRequest(session.getId(), "좋아요", Sentiment.POS, false, List.of("내용"), "kobert-v1");

        // when
        FeedbackView view = feedbackService.submit("EVT-A", req, "client-1");

        // then
        assertThat(view.id()).isNotNull();
        assertThat(view.text()).isEqualTo("좋아요");
        assertThat(feedbackRepository.findById(view.id())).isPresent();
    }

    @Test
    void 이벤트가_LIVE가_아니면_EVENT_NOT_LIVE() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.DRAFT);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        em.flush();
        em.clear();
        FeedbackSubmitRequest req =
                new FeedbackSubmitRequest(session.getId(), "x", Sentiment.POS, false, List.of(), "v1");

        // when/then
        assertThatThrownBy(() -> feedbackService.submit("EVT-A", req, "c1"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_LIVE);
    }

    @Test
    void 세션이_CLOSED면_SESSION_CLOSED() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.CLOSED);
        em.flush();
        em.clear();
        FeedbackSubmitRequest req =
                new FeedbackSubmitRequest(session.getId(), "x", Sentiment.POS, false, List.of(), "v1");

        // when/then
        assertThatThrownBy(() -> feedbackService.submit("EVT-A", req, "c1"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_CLOSED);
    }

    @Test
    void 다른_이벤트코드로_제출하면_SESSION_NOT_FOUND() {
        // given
        Event eventA = persistEvent("EVT-A", EventStatus.LIVE);
        Event eventB = persistEvent("EVT-B", EventStatus.LIVE);
        Session sessionA = persistSession(eventA, SessionStatus.ACTIVE);
        em.flush();
        em.clear();
        FeedbackSubmitRequest req =
                new FeedbackSubmitRequest(sessionA.getId(), "x", Sentiment.POS, false, List.of(), "v1");

        // when/then — EVT-B 경로로 EVT-A 소속 세션 제출 시도
        assertThatThrownBy(() -> feedbackService.submit("EVT-B", req, "c1"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    // ===================== submit 레이트리밋 =====================

    @Test
    void 같은_키로_분당_4번째_제출은_RATE_LIMIT_EXCEEDED() {
        // given
        Event event = persistEvent("EVT-A", EventStatus.LIVE);
        Session session = persistSession(event, SessionStatus.ACTIVE);
        em.flush();
        em.clear();
        FeedbackSubmitRequest req =
                new FeedbackSubmitRequest(session.getId(), "x", Sentiment.POS, false, List.of(), "v1");

        // when — 같은 (session, client)로 3번은 통과
        feedbackService.submit("EVT-A", req, "same-client");
        feedbackService.submit("EVT-A", req, "same-client");
        feedbackService.submit("EVT-A", req, "same-client");

        // then — 4번째는 거부
        assertThatThrownBy(() -> feedbackService.submit("EVT-A", req, "same-client"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}

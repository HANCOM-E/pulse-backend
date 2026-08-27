package com.hancome.pulse.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import com.hancome.pulse.feedback.Feedback;
import com.hancome.pulse.feedback.FeedbackRepository;
import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.FeedbackStatus;
import com.hancome.pulse.feedback.Sentiment;
import com.hancome.pulse.report.dto.SessionReportResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * SessionReportService·SessionReportFiller 통합 테스트. 새로 추가된 것만 검증한다 — 세션 CLOSED 게이트, 세션당 멱등, 자료요약
 * 저장, 워커 채우기, 주최자 리셋. (LLM은 캔값으로 스텁.)
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SessionReportServiceTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final SessionReportRepository sessionReportRepository;
    private final TestEntityManager em;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private SessionReportService service;
    private SessionReportFiller filler;

    SessionReportServiceTest(
            UserRepository userRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            FeedbackRepository feedbackRepository,
            SessionReportRepository sessionReportRepository,
            TestEntityManager em) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        service = new SessionReportService(sessionRepository, sessionReportRepository, eventPublisher);
        FeedbackService feedbackService =
                new FeedbackService(eventRepository, sessionRepository, feedbackRepository, event -> {});
        RestClient.Builder builder = RestClient.builder().baseUrl(ReportSummaryGenerator.OPENROUTER_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.manyTimes(), requestTo(ReportSummaryGenerator.OPENROUTER_URL + "/chat/completions"))
                .andRespond(
                        withSuccess("{\"choices\":[{\"message\":{\"content\":\"요약\"}}]}", MediaType.APPLICATION_JSON));
        ReportSummaryGenerator summaryGenerator = new ReportSummaryGenerator(builder.build(), "test-model");
        filler = new SessionReportFiller(sessionReportRepository, feedbackService, summaryGenerator);
    }

    private User persistOwner(String email) {
        return userRepository.save(new User(email, "hashed-pw"));
    }

    private Event persistEvent(User owner, String code) {
        Event event = new Event(code, "이벤트", null, LocalDate.of(2026, 8, 15), owner);
        event.setStatus(EventStatus.LIVE);
        return eventRepository.save(event);
    }

    private Session persistSession(Event event, SessionStatus status) {
        Session session = sessionRepository.save(new Session(event, "세션", 1));
        session.setStatus(status);
        return session;
    }

    private void saveVisibleFeedback(Session session, Sentiment sentiment) {
        Feedback f = new Feedback(session, "소감", sentiment, false, "v1", List.of("발표속도"));
        f.setStatus(FeedbackStatus.VISIBLE);
        feedbackRepository.save(f);
    }

    // ===================== generate 게이트·멱등 =====================

    @Test
    void 세션이_CLOSED가_아니면_SESSION_NOT_CLOSED() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.ACTIVE); // 진행 중
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.generate("EVT-A", session.getId(), "자료요약"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_CLOSED);
    }

    @Test
    void 다른_이벤트_세션이면_SESSION_NOT_FOUND() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.generate("EVT-OTHER", session.getId(), null))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void CLOSED면_GENERATING으로_저장되고_자료요약이_남는다() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        em.flush();
        em.clear();

        SessionReportResponse res = service.generate("EVT-A", session.getId(), "발표 자료 요약본");
        em.flush();
        em.clear();

        assertThat(res.status()).isEqualTo(ReportStatus.GENERATING);
        SessionReport saved =
                sessionReportRepository.findBySession_Id(session.getId()).orElseThrow();
        assertThat(saved.getMaterialSummary()).isEqualTo("발표 자료 요약본");
    }

    @Test
    void 이미_생성중이면_REPORT_ALREADY_EXISTS() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        service.generate("EVT-A", session.getId(), null);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.generate("EVT-A", session.getId(), "다시"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
    }

    // ===================== 워커 채우기 =====================

    @Test
    void 워커가_세션_집계와_요약을_채우고_GENERATED로_전이한다() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        saveVisibleFeedback(session, Sentiment.POS);
        saveVisibleFeedback(session, Sentiment.NEG);
        SessionReportResponse res = service.generate("EVT-A", session.getId(), "자료요약");
        em.flush();
        em.clear();

        filler.fill(res.id(), "EVT-A", session.getId());
        em.flush();
        em.clear();

        SessionReport filled = sessionReportRepository.findById(res.id()).orElseThrow();
        assertThat(filled.getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(filled.getSummaryText()).isEqualTo("요약");
        assertThat(filled.getSentimentBreakdown().POS()).isEqualTo(1);
        assertThat(filled.getSentimentBreakdown().NEG()).isEqualTo(1);
        assertThat(filled.getGeneratedAt()).isNotNull();
    }

    // ===================== reset =====================

    @Test
    void 소유자_리셋이_리포트를_지워_재생성을_연다() {
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        service.generate("EVT-A", session.getId(), null);
        em.flush();
        em.clear();

        service.reset(owner.getId(), "EVT-A", session.getId());
        em.flush();
        em.clear();

        assertThat(sessionReportRepository.findBySession_Id(session.getId())).isEmpty();
        // 재생성 가능
        SessionReportResponse again = service.generate("EVT-A", session.getId(), null);
        assertThat(again.status()).isEqualTo(ReportStatus.GENERATING);
    }

    @Test
    void 소유자가_아니면_리셋은_NOT_OWNER() {
        User owner = persistOwner("a@pulse.dev");
        User other = persistOwner("b@pulse.dev");
        Event event = persistEvent(owner, "EVT-A");
        Session session = persistSession(event, SessionStatus.CLOSED);
        service.generate("EVT-A", session.getId(), null);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.reset(other.getId(), "EVT-A", session.getId()))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }
}

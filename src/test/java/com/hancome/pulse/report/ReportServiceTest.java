package com.hancome.pulse.report;

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
import com.hancome.pulse.feedback.Feedback;
import com.hancome.pulse.feedback.FeedbackRepository;
import com.hancome.pulse.feedback.FeedbackService;
import com.hancome.pulse.feedback.FeedbackStatus;
import com.hancome.pulse.feedback.Sentiment;
import com.hancome.pulse.report.dto.PublicReport;
import com.hancome.pulse.report.dto.ReportResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestConstructor;

/**
 * ReportService·ReportGenerationWorker 통합 테스트. 생성 조건·auth 다형 조회·토글·비동기 채우기(워커는 동기로 직접 호출)를 실제
 * 실행으로 검증한다.
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReportServiceTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReportRepository reportRepository;
    private final TestEntityManager em;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private ReportService reportService;
    private ReportGenerationWorker worker;

    ReportServiceTest(
            UserRepository userRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            FeedbackRepository feedbackRepository,
            ReportRepository reportRepository,
            TestEntityManager em) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.reportRepository = reportRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        reportService = new ReportService(eventRepository, reportRepository, eventPublisher);
        FeedbackService feedbackService = new FeedbackService(eventRepository, sessionRepository, feedbackRepository);
        worker = new ReportGenerationWorker(reportRepository, feedbackService, new ReportSummaryGenerator());
    }

    private User persistOwner(String email) {
        return userRepository.save(new User(email, "hashed-pw"));
    }

    private Event persistEvent(User owner, String code, EventStatus status) {
        Event event = new Event(code, "이벤트", null, LocalDate.of(2026, 8, 15), owner);
        event.setStatus(status);
        return eventRepository.save(event);
    }

    private void saveVisibleFeedback(Session session, Sentiment sentiment) {
        Feedback f = new Feedback(session, "소감", sentiment, false, "v1", List.of("발표속도"));
        f.setStatus(FeedbackStatus.VISIBLE);
        feedbackRepository.save(f);
    }

    // ===================== generate 조건 =====================

    @Test
    void ENDED가_아니면_EVENT_NOT_ENDED() {
        // given
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.LIVE);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.generate(owner.getId(), "EVT-A"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_ENDED);
    }

    @Test
    void 소유자가_아니면_NOT_OWNER() {
        // given
        User owner = persistOwner("a@pulse.dev");
        User other = persistOwner("b@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.generate(other.getId(), "EVT-A"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 정상이면_GENERATING으로_저장된다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        em.flush();
        em.clear();

        // when
        ReportResponse res = reportService.generate(owner.getId(), "EVT-A");

        // then
        assertThat(res.status()).isEqualTo(ReportStatus.GENERATING);
        assertThat(reportRepository.findByEvent_Code("EVT-A")).isPresent();
    }

    @Test
    void 이미_생성중이면_REPORT_ALREADY_EXISTS() {
        // given
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        reportService.generate(owner.getId(), "EVT-A"); // GENERATING 생성
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.generate(owner.getId(), "EVT-A"))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
    }

    // ===================== 워커 채우기 =====================

    @Test
    void 워커가_집계와_요약을_채우고_GENERATED로_전이한다() {
        // given — ENDED 이벤트 + 세션 + VISIBLE 소감(POS 2, NEG 1)
        User owner = persistOwner("a@pulse.dev");
        Event event = persistEvent(owner, "EVT-A", EventStatus.ENDED);
        Session session = sessionRepository.save(new Session(event, "세션", 1));
        session.setStatus(SessionStatus.ACTIVE);
        saveVisibleFeedback(session, Sentiment.POS);
        saveVisibleFeedback(session, Sentiment.POS);
        saveVisibleFeedback(session, Sentiment.NEG);
        Long reportId = reportService.generate(owner.getId(), "EVT-A").id();
        em.flush();
        em.clear();

        // when — 비동기 워커를 동기로 직접 호출
        worker.fill(reportId, "EVT-A");
        em.flush();
        em.clear();

        // then
        Report found = reportRepository.findById(reportId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(found.getSummaryText()).isEqualTo("(요약 생성 예정)");
        assertThat(found.getSentimentBreakdown().POS()).isEqualTo(2);
        assertThat(found.getSentimentBreakdown().NEG()).isEqualTo(1);
        assertThat(found.getTopKeywords()).extracting(kc -> kc.keyword()).containsExactly("발표속도");
    }

    // ===================== getReport 다형 =====================

    @Test
    void 소유자는_전체_뷰를_받는다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        reportService.generate(owner.getId(), "EVT-A");
        em.flush();
        em.clear();

        // when
        Object res = reportService.getReport("EVT-A", owner.getId());

        // then
        assertThat(res).isInstanceOf(ReportResponse.class);
    }

    @Test
    void 게스트는_공개된_경우만_PublicReport를_받는다() {
        // given — 공개 토글된 리포트
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        reportService.generate(owner.getId(), "EVT-A");
        reportService.toggle(owner.getId(), "EVT-A", true);
        em.flush();
        em.clear();

        // when — 게스트(ownerId=null)
        Object res = reportService.getReport("EVT-A", null);

        // then
        assertThat(res).isInstanceOf(PublicReport.class);
    }

    @Test
    void 게스트에게_비공개면_REPORT_NOT_FOUND로_숨긴다() {
        // given — 비공개(기본)
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        reportService.generate(owner.getId(), "EVT-A");
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.getReport("EVT-A", null))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void 리포트가_없으면_REPORT_NOT_FOUND() {
        // given
        User owner = persistOwner("a@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.getReport("EVT-A", owner.getId()))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.REPORT_NOT_FOUND);
    }

    // ===================== toggle =====================

    @Test
    void 소유자가_아니면_토글이_NOT_OWNER() {
        // given
        User owner = persistOwner("a@pulse.dev");
        User other = persistOwner("b@pulse.dev");
        persistEvent(owner, "EVT-A", EventStatus.ENDED);
        reportService.generate(owner.getId(), "EVT-A");
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> reportService.toggle(other.getId(), "EVT-A", true))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }
}

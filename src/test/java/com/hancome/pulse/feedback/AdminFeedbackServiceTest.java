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
import com.hancome.pulse.feedback.dto.AdminFeedbackView;
import com.hancome.pulse.feedback.dto.FeedbackListResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

/** AdminFeedbackService 통합 테스트. 필터 조합·상태 전이·소유권 체인을 실제 실행으로 검증한다. */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminFeedbackServiceTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final TestEntityManager em;
    private AdminFeedbackService adminFeedbackService;

    AdminFeedbackServiceTest(
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
        adminFeedbackService = new AdminFeedbackService(feedbackRepository);
    }

    private User persistOwner(String email) {
        return userRepository.save(new User(email, "hashed-pw"));
    }

    private Session persistSession(User owner, String code) {
        Event event = new Event(code, "이벤트", null, LocalDate.of(2026, 8, 15), owner);
        event.setStatus(EventStatus.LIVE);
        eventRepository.save(event);
        return sessionRepository.save(new Session(event, "세션", 1));
    }

    private Long saveFeedback(Session session, boolean toxic, FeedbackStatus status) {
        Feedback f = new Feedback(session, "소감", Sentiment.NEU, toxic, "v1", List.of("a"));
        f.setStatus(status);
        return feedbackRepository.save(f).getId();
    }

    // ===================== 상태 전이 =====================

    @Test
    void hide는_HIDDEN으로_전이한다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        Long fid = saveFeedback(session, true, FeedbackStatus.VISIBLE);
        em.flush();
        em.clear();

        // when
        AdminFeedbackView view = adminFeedbackService.hide(owner.getId(), fid);

        // then
        assertThat(view.status()).isEqualTo(FeedbackStatus.HIDDEN);
        assertThat(feedbackRepository.findById(fid).orElseThrow().getStatus()).isEqualTo(FeedbackStatus.HIDDEN);
    }

    @Test
    void show는_VISIBLE로_되돌린다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        Long fid = saveFeedback(session, true, FeedbackStatus.HIDDEN);
        em.flush();
        em.clear();

        // when/then
        assertThat(adminFeedbackService.show(owner.getId(), fid).status()).isEqualTo(FeedbackStatus.VISIBLE);
    }

    @Test
    void delete는_DELETED로_전이하고_또_삭제하면_충돌() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        Long fid = saveFeedback(session, false, FeedbackStatus.VISIBLE);
        adminFeedbackService.delete(owner.getId(), fid);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> adminFeedbackService.delete(owner.getId(), fid))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.FEEDBACK_ALREADY_DELETED);
    }

    @Test
    void 이미_DELETED면_hide도_충돌() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        Long fid = saveFeedback(session, false, FeedbackStatus.DELETED);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> adminFeedbackService.hide(owner.getId(), fid))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.FEEDBACK_ALREADY_DELETED);
    }

    // ===================== 소유권 =====================

    @Test
    void 남의_소감을_건드리면_NOT_OWNER() {
        // given
        User owner = persistOwner("a@pulse.dev");
        User other = persistOwner("b@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        Long fid = saveFeedback(session, false, FeedbackStatus.VISIBLE);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> adminFeedbackService.hide(other.getId(), fid))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 없는_소감이면_FEEDBACK_NOT_FOUND() {
        // given
        User owner = persistOwner("a@pulse.dev");

        // when/then
        assertThatThrownBy(() -> adminFeedbackService.hide(owner.getId(), 999999L))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND);
    }

    // ===================== 필터 조회 =====================

    @Test
    void includeHidden_false면_HIDDEN과_DELETED가_빠진다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        saveFeedback(session, false, FeedbackStatus.VISIBLE);
        saveFeedback(session, false, FeedbackStatus.HIDDEN);
        saveFeedback(session, false, FeedbackStatus.DELETED);
        em.flush();
        em.clear();

        // when
        FeedbackListResponse res = adminFeedbackService.list(owner.getId(), null, null, null, false);

        // then — VISIBLE 1건만
        assertThat(res.items()).extracting(AdminFeedbackView::status).containsExactly(FeedbackStatus.VISIBLE);
    }

    @Test
    void includeHidden_true면_HIDDEN도_포함되고_DELETED는_여전히_제외() {
        // given
        User owner = persistOwner("a@pulse.dev");
        Session session = persistSession(owner, "EVT-A");
        saveFeedback(session, false, FeedbackStatus.VISIBLE);
        saveFeedback(session, false, FeedbackStatus.HIDDEN);
        saveFeedback(session, false, FeedbackStatus.DELETED);
        em.flush();
        em.clear();

        // when
        FeedbackListResponse res = adminFeedbackService.list(owner.getId(), null, null, null, true);

        // then — VISIBLE + HIDDEN 2건, DELETED 제외
        assertThat(res.items())
                .extracting(AdminFeedbackView::status)
                .containsExactlyInAnyOrder(FeedbackStatus.VISIBLE, FeedbackStatus.HIDDEN);
    }

    @Test
    void toxic_필터와_소유권_범위가_적용된다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        User other = persistOwner("b@pulse.dev");
        Session mine = persistSession(owner, "EVT-A");
        Session theirs = persistSession(other, "EVT-B");
        saveFeedback(mine, true, FeedbackStatus.VISIBLE); // 내 것, toxic
        saveFeedback(mine, false, FeedbackStatus.VISIBLE); // 내 것, 비toxic
        saveFeedback(theirs, true, FeedbackStatus.VISIBLE); // 남의 것
        em.flush();
        em.clear();

        // when — 내 이벤트의 toxic만
        FeedbackListResponse res = adminFeedbackService.list(owner.getId(), null, null, true, false);

        // then
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).toxic()).isTrue();
    }
}

package com.hancome.pulse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.auth.UserRepository;
import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

/** SessionService 도메인 로직 테스트. {@code @DataJpaTest} + 실제 레포로 서비스를 직접 조립하고, write 뒤 {@code em.flush()/clear()}로 DB 상태를 다시 읽어 검증한다. */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SessionServiceTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final TestEntityManager em;
    private SessionService sessionService;

    SessionServiceTest(
            UserRepository userRepository,
            EventRepository eventRepository,
            SessionRepository sessionRepository,
            TestEntityManager em) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(eventRepository, sessionRepository);
    }

    private User persistOwner(String email) {
        return userRepository.save(new User(email, "hashed-pw"));
    }

    /** 소유자의 이벤트를 저장하고 공개 코드를 돌려준다. */
    private String persistEvent(User owner, String code) {
        eventRepository.save(new Event(code, "이벤트", null, owner));
        return code;
    }

    /** 세션을 하나 만들고 sessionId를 돌려준다. */
    private Long createSession(Long ownerId, String code, String title, int order) {
        return sessionService
                .create(new SessionCreateRequest(title, order), ownerId, code)
                .id();
    }

    // ===================== create =====================

    @Test
    void 소유자가_아니면_세션생성이_NOT_OWNER() {
        // given
        User ownerA = persistOwner("a@pulse.dev");
        User ownerB = persistOwner("b@pulse.dev");
        String code = persistEvent(ownerA, "EVT-A");
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> sessionService.create(new SessionCreateRequest("세션", 1), ownerB.getId(), code))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 없는_이벤트코드로_세션생성하면_EVENT_NOT_FOUND() {
        // given
        User owner = persistOwner("a@pulse.dev");

        // when/then
        assertThatThrownBy(() -> sessionService.create(new SessionCreateRequest("세션", 1), owner.getId(), "NOPE"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    // ===================== update =====================

    @Test
    void 제목_순서_상태는_보낸_필드만_부분수정된다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String code = persistEvent(owner, "EVT-A");
        Long sessionId = createSession(owner.getId(), code, "원래제목", 1);
        em.flush();
        em.clear();

        // when
        sessionService.update(
                new SessionUpdateRequest("새제목", null, SessionStatus.ACTIVE), owner.getId(), code, sessionId);
        em.flush();
        em.clear();

        // then
        Session found = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("새제목");
        assertThat(found.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(found.getOrder()).isEqualTo(1);
    }

    @Test
    void 없는_세션을_수정하면_SESSION_NOT_FOUND() {
        // given
        User owner = persistOwner("a@pulse.dev");

        // when/then
        assertThatThrownBy(() -> sessionService.update(
                        new SessionUpdateRequest("x", null, null), owner.getId(), "NOPE", 999999L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    // ===================== delete =====================

    @Test
    void 삭제하면_status가_DELETED가_된다() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String code = persistEvent(owner, "EVT-A");
        Long sessionId = createSession(owner.getId(), code, "세션", 1);
        em.flush();
        em.clear();

        // when
        sessionService.delete(owner.getId(), code, sessionId);
        em.flush();
        em.clear();

        // then
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.DELETED);
    }

    @Test
    void 이미_삭제된_세션을_또_삭제하면_SESSION_ALREADY_DELETED() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String code = persistEvent(owner, "EVT-A");
        Long sessionId = createSession(owner.getId(), code, "세션", 1);
        sessionService.delete(owner.getId(), code, sessionId);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> sessionService.delete(owner.getId(), code, sessionId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_ALREADY_DELETED);
    }

    // ===================== listPublic / 소유권 =====================

    @Test
    void listPublic은_order_오름차순으로_반환한다() {
        // given
        User user = persistOwner("test@test.com");
        String code = persistEvent(user, "asdasdsd");
        createSession(user.getId(), code, "3", 3);
        createSession(user.getId(), code, "1", 1);
        createSession(user.getId(), code, "2", 2);
        em.flush();
        em.clear();

        // when
        SessionListResponse res = sessionService.listPublic(code);

        // then
        assertThat(res.items()).extracting(SessionView::order).containsExactly(1, 2, 3);
    }

    @Test
    void 부모_이벤트_소유자가_아니면_세션수정도_NOT_OWNER() {
        // given
        User user1 = persistOwner("test@test.com");
        User user2 = persistOwner("test2@test.com");
        String code = persistEvent(user1, "asdasdasd");
        Long a = createSession(user1.getId(), code, "1", 1);

        // when/then
        assertThatThrownBy(
                        () -> sessionService.update(new SessionUpdateRequest("3", null, null), user2.getId(), code, a))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
        assertThatThrownBy(() -> sessionService.delete(user2.getId(), code, a))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 다른_이벤트코드로_세션수정하면_SESSION_NOT_FOUND() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String codeA = persistEvent(owner, "EVT-A");
        String codeB = persistEvent(owner, "EVT-B");
        Long sessionId = createSession(owner.getId(), codeA, "세션", 1);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> sessionService.update(
                        new SessionUpdateRequest("x", null, null), owner.getId(), codeB, sessionId))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void status를_DELETED로_수정하면_VALIDATION_ERROR() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String code = persistEvent(owner, "EVT-A");
        Long sessionId = createSession(owner.getId(), code, "세션", 1);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> sessionService.update(
                        new SessionUpdateRequest(null, null, SessionStatus.DELETED), owner.getId(), code, sessionId))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 이미_삭제된_세션을_수정하면_SESSION_ALREADY_DELETED() {
        // given
        User owner = persistOwner("a@pulse.dev");
        String code = persistEvent(owner, "EVT-A");
        Long sessionId = createSession(owner.getId(), code, "세션", 1);
        sessionService.delete(owner.getId(), code, sessionId);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> sessionService.update(
                        new SessionUpdateRequest("x", null, null), owner.getId(), code, sessionId))
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_ALREADY_DELETED);
    }

    @Test
    void 세션은_생성시_status가_기본_CLOSED다() {
        // given
        User user1 = persistOwner("test@test.com");
        String code = persistEvent(user1, "asdasdasd");

        // when
        SessionResponse res = sessionService.create(new SessionCreateRequest("세션", 1), user1.getId(), code);

        // then
        assertThat(res.status()).isEqualTo(SessionStatus.CLOSED);
    }
}

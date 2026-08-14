package com.hancome.pulse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.auth.UserRepository;
import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.dto.*;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

/**
 * EventService 도메인 로직 테스트. {@code @DataJpaTest}는 JPA 슬라이스만 올리므로(@Service 빈은 안 올라옴) 실제 레포로
 * {@link EventService}를 직접 엮어 진짜 H2에 대해 검증한다. 각 테스트는 트랜잭션 안에서 돌고 끝나면 롤백된다.
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventServiceTest {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final TestEntityManager em;
    private EventService eventService;

    EventServiceTest(EventRepository eventRepository, UserRepository userRepository, TestEntityManager em) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        // @DataJpaTest는 @Service 빈을 안 올리므로 실제 레포로 직접 조립한다.
        eventService = new EventService(eventRepository, userRepository);
    }

    /** 소유자로 쓸 User를 저장해 반환. */
    private User persistOwner(String email) {
        return userRepository.save(new User(email, "hashed-pw"));
    }

    private static final String TEST_EMAIL = "host@pulse.dev";

    private static final LocalDate TEST_EVENT_DATE = LocalDate.of(2026, 8, 15);

    private static final EventCreateRequest TEST_CREATE_REQUEST =
            new EventCreateRequest("title", "description", TEST_EVENT_DATE);

    // ===================== create =====================

    @Test
    void 이벤트를_생성하면_DRAFT와_공개코드가_부여되고_저장된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        EventCreateRequest req = new EventCreateRequest("봄 컨퍼런스", "설명입니다", TEST_EVENT_DATE);

        // when
        EventResponse res = eventService.create(owner.getId(), req);
        em.flush();
        em.clear();

        // then
        assertThat(res.id()).isNotNull();
        assertThat(res.code()).isNotBlank();
        assertThat(res.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(res.ownerId()).isEqualTo(owner.getId());
        assertThat(res.title()).isEqualTo("봄 컨퍼런스");
        assertThat(res.eventDate()).isEqualTo(TEST_EVENT_DATE);
        assertThat(eventRepository.findByCode(res.code())).isPresent();
    }

    // ===================== getPublic =====================

    @Test
    void 없는_코드로_공개조회하면_EVENT_NOT_FOUND() {
        // when/then
        assertThatThrownBy(() -> eventService.getPublic("nope"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void DELETED_이벤트는_공개조회에서_EVENT_NOT_FOUND로_숨겨진다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        eventService.delete(owner.getId(), code);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> eventService.getPublic(code))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    // ===================== listMine =====================

    @Test
    void 내_이벤트만_나오고_DELETED는_빠진다() {
        // given
        User ownerA = persistOwner(TEST_EMAIL);
        User ownerB = persistOwner("tes@pulse.dev");

        String deletedCode =
                eventService.create(ownerA.getId(), TEST_CREATE_REQUEST).code();
        eventService.delete(ownerA.getId(), deletedCode);
        eventService.create(ownerA.getId(), TEST_CREATE_REQUEST);
        eventService.create(ownerB.getId(), TEST_CREATE_REQUEST);
        em.flush();
        em.clear();

        // when
        EventListResponse res = eventService.listMine(ownerA.getId());

        // then
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().getFirst().ownerId()).isEqualTo(ownerA.getId());
    }

    // ===================== update =====================

    @Test
    void 소유자가_아니면_NOT_OWNER() {
        // given
        User ownerA = persistOwner(TEST_EMAIL);
        User ownerB = persistOwner("tes@pulse.dev");
        String code = eventService.create(ownerA.getId(), TEST_CREATE_REQUEST).code();
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() ->
                        eventService.update(ownerB.getId(), code, new EventUpdateRequest(null, "desc", null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_OWNER);
    }

    @Test
    void 제목과_설명은_보낸_필드만_부분수정된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();

        // when
        eventService.update(owner.getId(), code, new EventUpdateRequest("nextTitle", null, null, null));
        em.flush();
        em.clear();

        // then
        EventView view = eventService.getPublic(code);
        assertThat(view.title()).isEqualTo("nextTitle");
        assertThat(view.description()).isEqualTo("description");
    }

    @Test
    void 행사날짜도_보낸_경우_수정된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        LocalDate newDate = LocalDate.of(2026, 12, 25);

        // when
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, null, newDate));
        em.flush();
        em.clear();

        // then
        assertThat(eventService.getPublic(code).eventDate()).isEqualTo(newDate);
    }

    @Test
    void DRAFT에서_세션이_있으면_LIVE로_전이된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        attachSession(code);

        // when
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.LIVE, null));
        em.flush();
        em.clear();

        // then
        assertThat(eventRepository.findByCode(code).orElseThrow().getStatus()).isEqualTo(EventStatus.LIVE);
    }

    @Test
    void DRAFT에서_세션이_0개면_LIVE_전이가_INVALID_EVENT_STATE_TRANSITION() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> eventService.update(
                        owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.LIVE, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_EVENT_STATE_TRANSITION);
    }

    @Test
    void DRAFT에서_ENDED로는_전이할_수_없다_INVALID() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        attachSession(code);

        // when/then
        assertThatThrownBy(() -> eventService.update(
                        owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.ENDED, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_EVENT_STATE_TRANSITION);
    }

    @Test
    void LIVE에서_ENDED로_전이된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        attachSession(code);
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.LIVE, null));
        em.flush();
        em.clear();

        // when
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.ENDED, null));
        em.flush();
        em.clear();

        // then
        assertThat(eventRepository.findByCode(code).orElseThrow().getStatus()).isEqualTo(EventStatus.ENDED);
    }

    @Test
    void ENDED로_전이되면_열린_세션이_CLOSED로_정합된다() {
        // given — LIVE 이벤트 + ACTIVE·CLOSED·DELETED 세션
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        Event event = eventRepository.findByCode(code).orElseThrow();
        Session active = new Session(event, "열림", 1);
        active.setStatus(SessionStatus.ACTIVE);
        Session closed = new Session(event, "이미마감", 2);
        closed.setStatus(SessionStatus.CLOSED);
        Session deleted = new Session(event, "삭제됨", 3);
        deleted.setStatus(SessionStatus.DELETED);
        em.persist(active);
        em.persist(closed);
        em.persist(deleted);
        em.flush();
        em.clear();
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.LIVE, null));
        em.flush();
        em.clear();

        // when
        eventService.update(owner.getId(), code, new EventUpdateRequest(null, null, EventStatus.ENDED, null));
        em.flush();
        em.clear();

        // then — ACTIVE만 CLOSED로, CLOSED·DELETED는 그대로
        assertThat(em.find(Session.class, active.getId()).getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(em.find(Session.class, closed.getId()).getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(em.find(Session.class, deleted.getId()).getStatus()).isEqualTo(SessionStatus.DELETED);
    }

    // ===================== delete =====================

    @Test
    void 삭제하면_status가_DELETED가_된다() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();

        // when
        eventService.delete(owner.getId(), code);
        em.flush();
        em.clear();

        // then
        assertThat(eventRepository.findByCode(code).orElseThrow().getStatus()).isEqualTo(EventStatus.DELETED);
    }

    @Test
    void 이미_삭제된_이벤트를_또_삭제하면_EVENT_ALREADY_DELETED() {
        // given
        User owner = persistOwner(TEST_EMAIL);
        String code = eventService.create(owner.getId(), TEST_CREATE_REQUEST).code();
        eventService.delete(owner.getId(), code);
        em.flush();
        em.clear();

        // when/then
        assertThatThrownBy(() -> eventService.delete(owner.getId(), code))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.EVENT_ALREADY_DELETED);
    }

    /** 코드로 이벤트를 찾아 세션 1개를 붙이고 flush/clear 한다(LIVE 전이 조건 충족용). */
    private void attachSession(String code) {
        Event event = eventRepository.findByCode(code).orElseThrow();
        em.persist(new Session(event, "세션1", 1));
        em.flush();
        em.clear();
    }
}

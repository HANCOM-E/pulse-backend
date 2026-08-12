package com.hancome.pulse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hancome.pulse.auth.User;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
// 이게 없으면 JUnit이 생성자 파라미터를 어떻게 채울지 몰라 ParameterResolutionException.
// ALL = 생성자의 모든 파라미터를 스프링 빈으로 자동 주입.
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventPersistenceTest {

    // 생성자 주입:
    // @DataJpaTest가 이 테스트 객체를 만들 때 EventRepository(프록시 빈)와
    // TestEntityManager(빈)를 이 생성자로 넣어준다 = DI.
    // ↓ 넘겨받은 걸 필드에 "저장"해야 @Test 메서드에서 쓸 수 있다. (네가 빠뜨린 부분)
    private final EventRepository eventRepository;
    private final TestEntityManager em;

    EventPersistenceTest(EventRepository eventRepository, TestEntityManager em) {
        this.eventRepository = eventRepository;
        this.em = em;
    }

    @Test
    void 이벤트를_저장하면_딸린_세션까지_함께_저장되고_다시_조회된다() {
        // given ---------------------------------------------------------------
        // Event.owner가 optional=false(NOT NULL FK)라, 영속화된 User가 먼저 있어야 한다.
        // UserRepository는 아직 없으니 TestEntityManager로 직접 persist. (new 하지 말고 주입받은 em 사용!)
        User owner = new User("host@pulse.dev", "hashed-pw");
        em.persist(owner);

        Event event = new Event("EVT-001", "봄 컨퍼런스", "설명입니다", java.time.LocalDate.of(2026, 8, 15), owner);
        // addSession이 sessions.add(s) + s.setEvent(this)를 둘 다 해준다(양방향 세팅).
        event.addSession(new Session(event, "오프닝", 1));
        event.addSession(new Session(event, "키노트", 2));

        // when ----------------------------------------------------------------
        // Event.sessions에 cascade=ALL이 걸려 있어, event 하나만 save해도 세션 2개가 함께 저장된다.
        Event saved = eventRepository.save(event);

        // 영속성 컨텍스트(1차 캐시)를 비운다.
        // flush: 지금까지의 변경을 DB로 밀어냄 / clear: 캐시를 통째로 비움
        // → 아래 findById가 캐시가 아니라 "진짜 DB"에서 새로 읽어오게 만든다.
        em.flush();
        em.clear();

        // then ----------------------------------------------------------------
        Event found = eventRepository.findById(saved.getId()).orElseThrow();
        // getSessions()는 LAZY라 이 줄에서 SQL이 한 번 더 나가며 세션이 로딩된다.
        // (@DataJpaTest는 트랜잭션 안이라 세션이 열려 있어 LAZY 로딩이 정상 동작)
        assertThat(found.getSessions()).hasSize(2);
    }

    @Test
    void 영속성_컨텍스트에서_분리된_뒤_LAZY_컬렉션에_접근하면_예외가_터진다() {
        // given: 위와 동일하게 저장 -----------------------------------------------
        User owner = new User("host2@pulse.dev", "hashed-pw");
        em.persist(owner);

        Event event = new Event("EVT-002", "가을 컨퍼런스", "설명입니다", java.time.LocalDate.of(2026, 8, 15), owner);
        event.addSession(new Session(event, "오프닝", 1));
        event.addSession(new Session(event, "키노트", 2));
        Long id = eventRepository.save(event).getId();

        em.flush();
        em.clear(); // 캐시 비우기 → 아래 findById는 DB에서 새로 읽고, sessions는 "아직 로딩 안 된 LAZY 프록시" 상태

        // when: 엔티티를 영속성 컨텍스트에서 "분리(detach)"한다 --------------------
        // detach된 엔티티는 더 이상 세션(영속성 컨텍스트)과 연결돼 있지 않다.
        Event found = eventRepository.findById(id).orElseThrow();
        em.getEntityManager().detach(found); // 여기서 세션과의 끈이 끊긴다

        // then: 이제 LAZY 컬렉션을 처음 건드리면, DB에서 로딩하려 하지만
        //       연결된 세션이 없어서 LazyInitializationException이 터진다.
        assertThatThrownBy(() -> found.getSessions().size()).isInstanceOf(LazyInitializationException.class);
    }
}

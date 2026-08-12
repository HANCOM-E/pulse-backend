package com.hancome.pulse.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.event.Event;
import jakarta.persistence.PersistenceUnitUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestConstructor;

@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReportPersistenceTest {

    private final TestEntityManager em;

    ReportPersistenceTest(TestEntityManager em) {
        this.em = em;
    }

    // ★ 주인 쪽 @OneToOne은 LAZY가 실제로 먹는다 (@ManyToOne과 동일).
    // Report.event는 FK(event_id)를 이 엔티티가 가진 "주인 쪽" @OneToOne이다.
    // 주인 쪽은 자기 FK 컬럼 값만 보면 되므로 @ManyToOne처럼 프록시를 만들 수 있어 LAZY가 그대로 동작한다.
    //   (LAZY가 안 먹는 건 mappedBy가 붙는 "역방향" @OneToOne 쪽. 상대 row 존재 여부를 알아야
    //    null/프록시를 정하는데, 그러려면 결국 조회를 해버려서 bytecode enhancement가 없으면 즉시 로딩된다.)
    //
    // 증명: find 직후 isLoaded(event)가 false이고 event가 HibernateProxy면 아직 로딩 안 된 LAZY 상태.
    //   (주의: detach 후 접근이 예외를 안 던지는 건 즉시 로딩의 증거가 아니다 — 세션이 열려 있으면
    //    detach가 프록시를 확실히 끊지 못해 접근 시 그냥 초기화된다. 그래서 isLoaded로 직접 확인한다.)
    @Test
    void Report_event_는_주인쪽_OneToOne이라_LAZY_프록시로_로딩된다() {
        // given
        User owner = new User("host@pulse.dev", "hashed-pw");
        em.persist(owner);
        Event event = new Event("EVT-RPT", "리포트용 이벤트", "설명", java.time.LocalDate.of(2026, 8, 15), owner);
        em.persist(event);

        Report report = new Report(event, "요약", "감정 분포", List.of("키워드"), false);
        em.persist(report);
        Long reportId = report.getId();

        em.flush();
        em.clear();

        // when: 캐시 비운 뒤 다시 조회
        Report found = em.find(Report.class, reportId);

        // then: event는 아직 로딩되지 않은 LAZY 프록시 (= 주인 쪽 @OneToOne LAZY가 동작함)
        PersistenceUnitUtil pu = em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(pu.isLoaded(found, "event")).isFalse();

        // 값을 실제로 건드리면 그때 초기화되어 로딩된다.
        assertThat(found.getEvent().getTitle()).isEqualTo("리포트용 이벤트");
        assertThat(pu.isLoaded(found, "event")).isTrue();
    }
}

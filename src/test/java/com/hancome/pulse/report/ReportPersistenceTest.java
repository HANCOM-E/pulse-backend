package com.hancome.pulse.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.event.Event;
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

    // ★ @OneToOne LAZY의 함정 (@ManyToOne과 다른 점).
    // Report.event는 fetch=LAZY, optional=false로 선언했지만,
    // @OneToOne은 주인 쪽(FK 보유)이라도 "bytecode enhancement" 없이는 LAZY가 안 먹고 즉시(EAGER) 로딩된다.
    // (@ManyToOne은 잘 되지만 @OneToOne은 안 되는 대표적 차이.)
    //
    // 그 증거: 영속성 컨텍스트에서 분리(detach)한 뒤에도 event 접근이 예외 없이 된다.
    //   → LAZY였다면 여기서 LazyInitializationException이 나야 한다. 안 난다 = 이미 즉시 로딩됨.
    // 진짜 LAZY로 만들려면 build.gradle에 hibernate-enhance(bytecode enhancement) 설정이 필요하다.
    @Test
    void Report_event_는_OneToOne이라_bytecode_enhancement_없이는_즉시로딩된다() {
        // given
        User owner = new User("host@pulse.dev", "hashed-pw");
        em.persist(owner);
        Event event = new Event("EVT-RPT", "리포트용 이벤트", "설명", owner);
        em.persist(event);

        Report report = new Report(event, "요약", "감정 분포", List.of("키워드"), false);
        em.persist(report);
        Long reportId = report.getId();

        em.flush();
        em.clear();

        // when: 다시 꺼내 분리
        Report found = em.find(Report.class, reportId);
        em.getEntityManager().detach(found);

        // then: 분리 후에도 접근 가능 = event가 이미 즉시 로딩되어 있었다는 뜻(= LAZY 미적용)
        assertThat(found.getEvent().getTitle()).isEqualTo("리포트용 이벤트");
    }
}

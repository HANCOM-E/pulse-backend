package com.hancome.pulse.event;

import com.hancome.pulse.auth.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "events") // 테이블명은 복수형으로 명시. 안 주면 클래스명(event) 그대로.
public class Event {

    @Id
    // IDENTITY: Postgres의 identity/serial 컬럼에 위임(DB가 PK 채번).
    // 배치 insert가 막히는 단점이 있지만 학습 규모엔 가장 단순해서 채택.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공유 링크 /e/{code} 에 쓰이는 이벤트 코드. 유니크 제약.
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    // 소유자(주최자). ★연관관계의 "주인" = FK(owner_id) 컬럼을 이 쪽이 가진다.
    // fetch = LAZY: owner를 실제로 꺼내 쓸 때까지 User SELECT를 미룬다.
    //   → 이벤트만 조회할 땐 User 조인이 안 나가고, event.getOwner().getXxx() 호출 순간 쿼리.
    //   (그래서 트랜잭션 밖에서 owner를 건드리면 LazyInitializationException이 난다 — 0-2 검증 포인트.)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    // enum을 이름 문자열로 저장. ORDINAL(0,1,2 숫자)로 두면 enum 상수 순서가 바뀔 때
    // 기존 행의 의미가 통째로 어긋나므로 실무에선 STRING이 사실상 표준.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    // 최초 저장 시각을 Hibernate가 자동 세팅. updatable=false로 이후 수정 방지.
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    // 1:N 세션. ★여기는 주인이 "아닌" 쪽 → mappedBy로 "FK는 Session.event가 가짐"을 선언.
    //   이 컬렉션엔 별도 FK 컬럼이 안 생긴다(주인 쪽 FK 하나로 관계 표현).
    // cascade = ALL + orphanRemoval: event를 저장/삭제하면 붙은 session도 함께 저장/삭제.
    //   → "Event 저장하면서 Session 2개 같이 저장" 테스트가 이 설정 덕에 가능.
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Session> sessions = new ArrayList<>();

    protected Event() {} // JPA가 리플렉션으로 쓰는 기본 생성자(외부 직접 호출 막으려 protected).

    public Event(String code, String title, String description, User owner) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.owner = owner;
    }

    // 양방향 연관관계 편의 메서드: 두 쪽(컬렉션 + Session.event)을 함께 세팅해야
    // 메모리 객체 그래프와 DB가 어긋나지 않는다. 항상 이걸로 세션을 붙인다.
    public void addSession(Session session) {
        sessions.add(session);
        session.setEvent(this);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getOwner() {
        return owner;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // 읽기 전용 뷰를 반환한다. 외부에서 getSessions().add()로 직접 넣으면
    // session.setEvent(this)가 누락돼 event_id NOT NULL 위반이 나므로,
    // 세션 추가는 항상 addSession()만 쓰도록 강제한다.
    public List<Session> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}

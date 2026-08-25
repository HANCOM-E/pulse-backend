package com.hancome.pulse.game;

import com.hancome.pulse.event.Event;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 참가자 미니게임(핀볼 레이스). 이벤트에 종속되며 주최자가 생성·상태전이·결과입력을 하고, 게스트는 참가/조회만 한다.
 *
 * <p>{@code status}는 {@link GameStatus}의 단방향 상태머신을 따른다. {@code ranking}은 종료 시 확정되는 참가자 PK 순위 배열(1등이
 * 인덱스 0)로, 핀볼 전용 결과 표현이다. 서버는 순위 계산을 하지 않고 주최자(프로젝터)가 올린 순서를 소속·중복만 검증해 그대로 신뢰한다(A안).
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 이벤트. 연관관계의 주인(FK event_id). fetch = LAZY.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType = GameType.PINBALL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status = GameStatus.DRAFT;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    // 순위 결과: 참가자 PK를 등수 순서(1등=index 0)로 담는 값 컬렉션.
    // @OrderColumn: 리스트 순서를 rank_order 컬럼으로 보존(등수 = 순서 그 자체). @ElementCollection이라 game_rankings
    // 별도 테이블에 저장되고 기본 LAZY. 종료 전엔 빈 리스트.
    @ElementCollection
    @CollectionTable(name = "game_rankings", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "rank_order")
    @Column(name = "participant_id")
    @BatchSize(size = 20) // 게임 목록 조회 시 랭킹 LAZY 로딩 N+1 완화
    private List<Long> ranking = new ArrayList<>();

    // 1:N 참가자. FK는 Participant.game가 가짐(mappedBy). 게임 저장/삭제 시 함께 처리.
    // @BatchSize: 게임 목록에서 각 게임의 참가자 컬렉션을 IN 절로 묶어 로딩(N+1 완화).
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 20)
    private List<Participant> participants = new ArrayList<>();

    protected Game() {} // JPA용 기본 생성자

    public Game(Event event, String title) {
        this.event = event;
        this.title = title;
    }

    // 양방향 연관관계 편의 메서드(Event.addSession과 동형). 항상 이걸로 참가자를 붙인다.
    public void addParticipant(Participant participant) {
        participants.add(participant);
        participant.setGame(this);
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getTitle() {
        return title;
    }

    public GameType getGameType() {
        return gameType;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // 읽기 전용 뷰 반환. 순위 확정은 setRanking으로만.
    public List<Long> getRanking() {
        return Collections.unmodifiableList(ranking);
    }

    // 결과 확정 시 순위 배열을 통째로 교체한다(등수 = 리스트 순서).
    public void setRanking(List<Long> ranking) {
        this.ranking = new ArrayList<>(ranking);
    }

    // 읽기 전용 뷰 반환. 참가자 추가는 항상 addParticipant()만 쓴다(setGame 누락 방지).
    public List<Participant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }
}

package com.hancome.pulse.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 미니게임 참가자. 게스트(비로그인)라 유저 식별이 없고, 브라우저가 보내는 {@code X-Client-Id}(FE localStorage UUID)로 식별한다.
 *
 * <p>{@code (game_id, client_id)} 유니크 제약이 재참가 upsert의 근거다: 같은 clientId로 다시 참가하면 새 행을 만들지 않고 닉네임만
 * 갱신한다. clientId는 내부 식별자라 공개 응답({@code ParticipantView})엔 절대 싣지 않는다.
 */
@Entity
@Table(name = "participants", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "client_id"}))
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 게임. 연관관계의 주인(FK game_id를 이 쪽이 가짐). fetch = LAZY.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    // 게스트 식별자(X-Client-Id). 공개 응답엔 제외.
    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String nickname;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant joinedAt;

    protected Participant() {} // JPA용 기본 생성자

    public Participant(Game game, String clientId, String nickname) {
        this.game = game;
        this.clientId = clientId;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public String getClientId() {
        return clientId;
    }

    public String getNickname() {
        return nickname;
    }

    // 재참가 시 닉네임만 갱신(같은 clientId → 같은 행).
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}

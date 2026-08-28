package com.hancome.pulse.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.auth.UserRepository;
import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventRepository;
import com.hancome.pulse.game.dto.GameView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 게임 목록 조회 500 회귀 방지: GameView가 LAZY {@code ranking}을 실체화하지 않고 담으면, 트랜잭션(세션)이 닫힌 뒤 JSON 직렬화 시점에
 * LazyInitializationException("no session")이 난다. from()이 트랜잭션 안에서 복사(List.copyOf)하는지 검증한다.
 *
 * <p>앰비언트 트랜잭션을 꺼(NOT_SUPPORTED) 매핑만 TransactionTemplate으로 감싸고, 반환된 DTO를 세션 밖에서 읽어 실제 직렬화 상황을 재현한다.
 */
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameViewLazyTest {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final GameRepository gameRepository;

    @Autowired
    private TransactionTemplate tx;

    GameViewLazyTest(UserRepository userRepository, EventRepository eventRepository, GameRepository gameRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.gameRepository = gameRepository;
    }

    // NOT_SUPPORTED라 커밋된 데이터가 롤백되지 않는다 → 공유 H2를 오염시키지 않게 직접 지운다.
    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(s -> {
            gameRepository.deleteAll();
            eventRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    void ranking을_세션_밖에서_읽어도_예외가_없다() {
        Long gameId = tx.execute(s -> {
            User owner = userRepository.save(new User("a@pulse.dev", "hashed-pw"));
            Event event = eventRepository.save(new Event("EVT-A", "이벤트", null, LocalDate.of(2026, 8, 15), owner));
            Game game = new Game(event, "핀볼");
            game.setRanking(List.of(11L, 22L, 33L));
            return gameRepository.save(game).getId();
        });

        // 새 트랜잭션에서 LAZY 상태로 로드해 매핑하고, 트랜잭션이 닫힌 뒤 DTO를 읽는다(직렬화 시점 재현).
        GameView view =
                tx.execute(s -> GameView.from(gameRepository.findById(gameId).orElseThrow()));

        assertThatCode(view::ranking).doesNotThrowAnyException();
        assertThat(view.ranking()).containsExactly(11L, 22L, 33L);
    }
}

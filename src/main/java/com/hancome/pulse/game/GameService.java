package com.hancome.pulse.game;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventRepository;
import com.hancome.pulse.game.dto.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미니게임(핀볼) 도메인 로직. 서버는 얇은 CRUD만 담당한다 — 순위는 계산하지 않고 주최자가 올린 순서를 소속·중복만 검증해 신뢰한다(A안).
 *
 * <p>DTO 매핑({@link GameView#from})은 LAZY 참가자·랭킹에 접근하므로 조회 메서드도 트랜잭션 안에서 매핑을 끝낸다. 주최자 검증은
 * {@code eventRepository.findByCode}로 이벤트를 찾은 뒤 {@code owner.id == userId}를 확인해 {@link
 * com.hancome.pulse.common.ErrorCode#NOT_OWNER}로 막는다({@code EventService.loadOwnedEvent}와 동일 패턴).
 *
 * <p><b>구현 노트(본문 미완성):</b> 아래 각 메서드는 시그니처·계약(Javadoc)만 확정돼 있고 본문은 학습용으로 비워 둔다. Javadoc의
 * 예외·상태 규칙을 계약처럼 참고해 채운다.
 */
@Service
public class GameService {
    private final GameRepository gameRepository;
    private final ParticipantRepository participantRepository;
    private final EventRepository eventRepository;

    public GameService(
            GameRepository gameRepository,
            ParticipantRepository participantRepository,
            EventRepository eventRepository) {
        this.gameRepository = gameRepository;
        this.participantRepository = participantRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * 게임을 생성한다(주최자). 상태 {@code DRAFT}, 종류 {@code PINBALL}로 시작한다.
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 소속 이벤트 코드
     * @param req 제목(1~50자)
     * @return 생성된 게임 뷰
     * @throws com.hancome.pulse.common.ApiException 이벤트 없으면 {@code EVENT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    @Transactional
    public GameView create(Long ownerId, String eventCode, GameCreateRequest req) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);

        Game game = new Game(event, req.title());
        gameRepository.save(game);
        return GameView.from(game);
    }

    /**
     * 이벤트의 전체 게임 목록을 조회한다(주최자, 최근 생성 순).
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 소속 이벤트 코드
     * @return 게임 목록 봉투
     * @throws com.hancome.pulse.common.ApiException 이벤트 없으면 {@code EVENT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    @Transactional
    public GameListResponse listOwned(Long ownerId, String eventCode) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);

        return GameListResponse.from(gameRepository.findByEvent_CodeOrderByCreatedAtDesc(eventCode));
    }

    /**
     * 공개용 "현재 게임"을 조회한다: {@code DRAFT}를 제외한 가장 최근 게임 1개.
     *
     * @param eventCode 소속 이벤트 코드
     * @return 현재 게임 뷰
     * @throws com.hancome.pulse.common.ApiException 조건에 맞는 게임이 없으면 {@code GAME_NOT_FOUND}
     */
    @Transactional
    public GameView getCurrent(String eventCode) {
        Game game = gameRepository
                .findFirstByEvent_CodeAndStatusNotOrderByCreatedAtDesc(eventCode, GameStatus.DRAFT)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND));
        return GameView.from(game);
    }

    /**
     * 게임 단건을 공개 조회한다(이벤트 스코프).
     *
     * @param eventCode 소속 이벤트 코드
     * @param gameId 게임 PK
     * @return 게임 뷰
     * @throws com.hancome.pulse.common.ApiException 해당 이벤트에 그 게임이 없으면 {@code GAME_NOT_FOUND}
     */
    @Transactional
    public GameView getPublic(String eventCode, Long gameId) {
        Game game = gameRepository
                .findByIdAndEvent_Code(gameId, eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND));

        return GameView.from(game);
    }

    /**
     * 게임 상태를 전이한다(주최자). {@link GameStatus}는 {@code DRAFT → OPEN → RUNNING → FINISHED}로만 한 단계씩 전진하는
     * 단방향 상태머신이다(되돌리기·건너뛰기 금지).
     *
     * <p><b>가드 규칙(★ 학습 포인트):</b>
     *
     * <ul>
     *   <li>현재 상태가 {@code FINISHED}면(터미널) → {@code GAME_ALREADY_FINISHED}
     *   <li>목표가 현재의 바로 다음 단계가 아니면(같은 상태·역방향·건너뛰기) → {@code INVALID_GAME_STATE_TRANSITION}
     *   <li>유효하면 상태만 갱신
     * </ul>
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 소속 이벤트 코드
     * @param gameId 게임 PK
     * @param req 목표 상태
     * @return 전이된 게임 뷰
     * @throws com.hancome.pulse.common.ApiException 이벤트/게임 없으면 {@code EVENT_NOT_FOUND}/{@code GAME_NOT_FOUND}, 소유자
     *     아니면 {@code NOT_OWNER}, 종료된 게임이면 {@code GAME_ALREADY_FINISHED}, 잘못된 전이면 {@code
     *     INVALID_GAME_STATE_TRANSITION}
     */
    @Transactional
    public GameView updateStatus(Long ownerId, String eventCode, Long gameId, GameStatusUpdateRequest req) {
        Game game = gameRepository
                .findByIdAndEvent_Code(gameId, eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND));
        if (!game.getEvent().getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);

        GameStatus from = game.getStatus();
        GameStatus to = req.status();
        if (from == GameStatus.FINISHED) throw new ApiException(ErrorCode.GAME_ALREADY_FINISHED);
        if (to.ordinal() != from.ordinal() + 1) throw new ApiException(ErrorCode.INVALID_GAME_STATE_TRANSITION);
        game.setStatus(to);

        return GameView.from(game);
    }

    /**
     * 게임 결과(순위)를 확정한다(주최자). 게임이 {@code RUNNING}일 때만 허용하며, 순위를 저장하고 {@code FINISHED}로 전이한다.
     *
     * <p><b>검증(A안 — 순위는 신뢰, 소속·중복만 확인):</b>
     *
     * <ul>
     *   <li>이미 {@code FINISHED}면 → {@code GAME_ALREADY_FINISHED}
     *   <li>{@code RUNNING}이 아니면(DRAFT·OPEN) → {@code INVALID_GAME_STATE_TRANSITION}
     *   <li>{@code ranking}의 원소가 모두 이 게임 소속 참가자 PK인지, 중복이 없는지 확인 → 위반이면 {@code VALIDATION_ERROR}
     *   <li>통과하면 {@code game.setRanking(...)} 후 {@code FINISHED}로 전이
     * </ul>
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 소속 이벤트 코드
     * @param gameId 게임 PK
     * @param req 참가자 PK 순위 배열(1등이 첫 원소)
     * @return 종료된 게임 뷰(순위 포함)
     * @throws com.hancome.pulse.common.ApiException 위 규칙 위반 시 각 코드
     */
    @Transactional
    public GameView submitResults(Long ownerId, String eventCode, Long gameId, GameResultRequest req) {
        Game game = gameRepository
                .findByIdAndEvent_Code(gameId, eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND));
        if (!game.getEvent().getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        GameStatus gameStatus = game.getStatus();
        if (gameStatus.equals(GameStatus.FINISHED)) throw new ApiException(ErrorCode.GAME_ALREADY_FINISHED);
        if (!gameStatus.equals(GameStatus.RUNNING)) throw new ApiException(ErrorCode.INVALID_GAME_STATE_TRANSITION);
        Set<Long> memberIds =
                game.getParticipants().stream().map(Participant::getId).collect(Collectors.toSet());

        List<Long> ranking = req.ranking();

        if (new HashSet<>(ranking).size() != ranking.size() || !memberIds.containsAll(ranking))
            throw new ApiException(ErrorCode.VALIDATION_ERROR);

        game.setRanking(ranking);
        game.setStatus(GameStatus.FINISHED);
        return GameView.from(game);
    }

    /**
     * 게임에 참가하거나 재참가한다(게스트). 게임이 {@code OPEN}일 때만 허용한다.
     *
     * <p>{@code (game, clientId)}로 기존 참가자를 찾아, 있으면 닉네임만 갱신(재참가, {@code created=false} → 200), 없으면 새
     * 참가자 생성({@code created=true} → 201)한다.
     *
     * @param eventCode 소속 이벤트 코드
     * @param gameId 게임 PK
     * @param req 닉네임(1~12자)
     * @param clientId 게스트 식별자({@code X-Client-Id}, 길이 캡 적용된 값)
     * @return 참가 결과(참가자 뷰 + 신규/갱신 여부)
     * @throws com.hancome.pulse.common.ApiException 해당 이벤트에 게임 없으면 {@code GAME_NOT_FOUND}, {@code OPEN}이 아니면
     *     {@code GAME_NOT_OPEN}
     */
    @Transactional
    public ParticipantJoinResult join(String eventCode, Long gameId, ParticipantJoinRequest req, String clientId) {
        Game game = gameRepository
                .findByIdAndEvent_Code(gameId, eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_NOT_FOUND));
        if (!game.getStatus().equals(GameStatus.OPEN)) throw new ApiException(ErrorCode.GAME_NOT_OPEN);
        Optional<Participant> participantOptional = participantRepository.findByGame_IdAndClientId(gameId, clientId);
        if (participantOptional.isEmpty()) {
            Participant participant = new Participant(game, clientId, req.nickname());
            participantRepository.save(participant);
            return new ParticipantJoinResult(ParticipantView.from(participant), true);
        } else {
            Participant participant = participantOptional.get();
            participant.setNickname(req.nickname());
            return new ParticipantJoinResult(ParticipantView.from(participant), false);
        }
    }
}

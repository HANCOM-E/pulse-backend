package com.hancome.pulse.game;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.game.dto.GameCreateRequest;
import com.hancome.pulse.game.dto.GameListResponse;
import com.hancome.pulse.game.dto.GameResultRequest;
import com.hancome.pulse.game.dto.GameStatusUpdateRequest;
import com.hancome.pulse.game.dto.GameView;
import com.hancome.pulse.game.dto.ParticipantJoinRequest;
import com.hancome.pulse.game.dto.ParticipantJoinResult;
import com.hancome.pulse.game.dto.ParticipantView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참가자 미니게임(핀볼). 생성·목록·상태전이·결과입력은 주최자(인증), 현재/단건 조회와 참가는 공개(게스트)다.
 *
 * <p>{@code @AuthenticationPrincipal Long userId}는 {@code JwtAuthenticationFilter}가 넣어둔 사용자 PK(공개 요청은 {@code
 * null}). 게스트 식별은 {@code X-Client-Id} 헤더로 한다.
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/games")
public class GameController {
    private static final int MAX_CLIENT_ID_LENGTH = 64;

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<GameView> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @Valid @RequestBody GameCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.create(userId, eventCode, req));
    }

    @GetMapping
    public GameListResponse listOwned(@AuthenticationPrincipal Long userId, @PathVariable String eventCode) {
        return gameService.listOwned(userId, eventCode);
    }

    @Operation(security = {}) // 공개 — DRAFT 제외 최근 게임 1개
    @GetMapping("/current")
    public GameView current(@PathVariable String eventCode) {
        return gameService.getCurrent(eventCode);
    }

    @Operation(security = {}) // 공개 단건
    @GetMapping("/{gameId}")
    public GameView getPublic(@PathVariable String eventCode, @PathVariable Long gameId) {
        return gameService.getPublic(eventCode, gameId);
    }

    @PatchMapping("/{gameId}")
    public GameView updateStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @PathVariable Long gameId,
            @Valid @RequestBody GameStatusUpdateRequest req) {
        return gameService.updateStatus(userId, eventCode, gameId, req);
    }

    @PostMapping("/{gameId}/results")
    public GameView submitResults(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @PathVariable Long gameId,
            @Valid @RequestBody GameResultRequest req) {
        return gameService.submitResults(userId, eventCode, gameId, req);
    }

    @Operation(security = {}) // 공개 — 게스트 참가/재참가
    @PostMapping("/{gameId}/participants")
    public ResponseEntity<ParticipantView> join(
            @PathVariable String eventCode,
            @PathVariable Long gameId,
            @Valid @RequestBody ParticipantJoinRequest req,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        // clientId가 참가자 식별자(재참가 upsert 키)라 필수. 없으면 dedup이 불가하므로 400.
        if (!StringUtils.hasText(clientId)) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String key = clientId.substring(0, Math.min(clientId.length(), MAX_CLIENT_ID_LENGTH));
        ParticipantJoinResult result = gameService.join(eventCode, gameId, req, key);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.participant());
    }
}

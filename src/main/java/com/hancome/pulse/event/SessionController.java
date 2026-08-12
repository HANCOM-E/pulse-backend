package com.hancome.pulse.event;

import com.hancome.pulse.event.dto.SessionCreateRequest;
import com.hancome.pulse.event.dto.SessionListResponse;
import com.hancome.pulse.event.dto.SessionResponse;
import com.hancome.pulse.event.dto.SessionUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 CRUD. 목록 조회만 공개, 나머지는 소유자(이벤트 주최자) 인증이 필요하다. 유저 식별자는 {@code @AuthenticationPrincipal}로
 * SecurityContext에서 가져온다(JWT 쿠키 → 필터가 채움).
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(security = {}) // 공개 엔드포인트 — 문서에서 전역 요구를 해제
    @GetMapping
    public SessionListResponse getPublic(@PathVariable String eventCode) {
        return sessionService.listPublic(eventCode);
    }

    @PostMapping
    public ResponseEntity<SessionResponse> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @Valid @RequestBody SessionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(req, userId, eventCode));
    }

    @PatchMapping("/{sessionId}")
    public SessionResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionUpdateRequest req) {
        return sessionService.update(req, userId, eventCode, sessionId);
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long userId, @PathVariable String eventCode, @PathVariable Long sessionId) {
        sessionService.delete(userId, eventCode, sessionId);
    }
}

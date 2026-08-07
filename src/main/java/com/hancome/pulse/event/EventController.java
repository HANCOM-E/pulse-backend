package com.hancome.pulse.event;

import com.hancome.pulse.event.dto.EventCreateRequest;
import com.hancome.pulse.event.dto.EventListResponse;
import com.hancome.pulse.event.dto.EventResponse;
import com.hancome.pulse.event.dto.EventUpdateRequest;
import com.hancome.pulse.event.dto.EventView;
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
 * 이벤트 CRUD. {@code /events/&#123;eventCode&#125;} 단건 조회만 공개, 나머지는 소유자 인증이 필요하다.
 *
 * <p>{@code @AuthenticationPrincipal Long userId}는 {@code JwtAuthenticationFilter}가 SecurityContext에 넣어둔
 * 사용자 PK다(공개 GET에서는 {@code null}).
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody EventCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(userId, req));
    }

    @GetMapping
    public EventListResponse listMine(@AuthenticationPrincipal Long userId) {
        return eventService.listMine(userId);
    }

    @Operation(security = {}) // 공개 엔드포인트 — 문서에서 전역 bearerAuth 요구를 해제
    @GetMapping("/{eventCode}")
    public EventView getPublic(@PathVariable String eventCode) {
        return eventService.getPublic(eventCode);
    }

    @PatchMapping("/{eventCode}")
    public EventResponse update(
            @AuthenticationPrincipal Long userId,
            @PathVariable String eventCode,
            @Valid @RequestBody EventUpdateRequest req) {
        return eventService.update(userId, eventCode, req);
    }

    @DeleteMapping("/{eventCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable String eventCode) {
        eventService.delete(userId, eventCode);
    }
}

package com.hancome.pulse.event;

import com.hancome.pulse.auth.User;
import com.hancome.pulse.auth.UserRepository;
import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.dto.EventCreateRequest;
import com.hancome.pulse.event.dto.EventListResponse;
import com.hancome.pulse.event.dto.EventResponse;
import com.hancome.pulse.event.dto.EventUpdateRequest;
import com.hancome.pulse.event.dto.EventView;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 CRUD 도메인 로직.
 * DTO 매핑은 지연 로딩된 {@code owner}에 접근하므로 트랜잭션 안(=이 서비스 메서드 내부)에서 끝낸다. 소유자 검증은 코드로 이벤트를 찾은 뒤
 * {@code owner.id == userId}를 확인해 {@code NOT_OWNER}로 막는다.
 */
@Service
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;

    public EventService(EventRepository eventRepository, UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    /**
     * 이벤트를 생성한다. 상태는 {@code DRAFT}로 시작하고 공개 코드를 채번한다.
     *
     * @param ownerId 인증된 주최자 PK
     * @param req 제목·설명
     * @return 생성된 이벤트 전체 뷰
     */
    @Transactional
    public EventResponse create(Long ownerId, EventCreateRequest req) {
        String code = generateUniqueCode();
        User owner = userRepository.getReferenceById(ownerId);
        Event event = new Event(code, req.title(), req.description(), owner);
        eventRepository.save(event);
        return EventResponse.from(event);
    }

    /**
     * 내 이벤트 목록을 조회한다({@code DELETED} 제외).
     *
     * @param ownerId 인증된 주최자 PK
     * @return 소유자의 이벤트 목록 봉투
     */
    public EventListResponse listMine(Long ownerId) {
        List<Event> eventList = eventRepository.findByOwner_IdAndStatusNot(ownerId, EventStatus.DELETED);
        return EventListResponse.from(eventList);
    }

    /**
     * 공개 코드로 이벤트를 조회한다(비인증 공개, 내부 식별자 제외 뷰).
     *
     * @param code 공개 이벤트 코드
     * @return 공개 뷰
     * @throws com.hancome.pulse.common.ApiException 없거나 {@code DELETED}면 {@code EVENT_NOT_FOUND}
     */
    public EventView getPublic(String code) {
        Event event = eventRepository.findByCode(code).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getStatus() == EventStatus.DELETED) throw new ApiException(ErrorCode.EVENT_NOT_FOUND);
        return EventView.from(event);
    }

    /**
     * 이벤트를 부분 수정한다(제목·설명·상태 전이). 소유자만 가능.
     *
     * @param ownerId 인증된 주최자 PK
     * @param code 공개 이벤트 코드
     * @param req 수정할 필드(보낸 것만 반영)
     * @return 수정된 전체 뷰
     * @throws com.hancome.pulse.common.ApiException 소유자 아니면 {@code NOT_OWNER}, 잘못된 상태 전이면
     *     {@code INVALID_EVENT_STATE_TRANSITION}
     */
    @Transactional
    public EventResponse update(Long ownerId, String code, EventUpdateRequest req) {
        Event event = loadOwnedEvent(ownerId, code);
        if (req.title() != null) {
            event.setTitle(req.title());
        }
        if (req.description() != null) {
            event.setDescription(req.description());
        }
        if (req.status() != null) {
            EventStatus from = event.getStatus();
            EventStatus to = req.status();
            boolean valid = (from == EventStatus.DRAFT
                            && to == EventStatus.LIVE
                            && !event.getSessions().isEmpty())
                    || (from == EventStatus.LIVE && to == EventStatus.ENDED);
            if (!valid) throw new ApiException(ErrorCode.INVALID_EVENT_STATE_TRANSITION);
            event.setStatus(to);
        }
        return EventResponse.from(event);
    }

    /**
     * 이벤트를 소프트 삭제한다({@code status = DELETED}). 소유자만 가능.
     *
     * @param ownerId 인증된 주최자 PK
     * @param code 공개 이벤트 코드
     * @throws com.hancome.pulse.common.ApiException 소유자 아니면 {@code NOT_OWNER}, 이미 삭제면 {@code EVENT_ALREADY_DELETED}
     */
    @Transactional
    public void delete(Long ownerId, String code) {
        Event event = loadOwnedEvent(ownerId, code);
        if (event.getStatus() == EventStatus.DELETED) throw new ApiException(ErrorCode.EVENT_ALREADY_DELETED);
        event.setStatus(EventStatus.DELETED);
    }

    /**
     * 코드로 이벤트를 찾고 소유자를 검증해 반환한다(쓰기 공통 진입점).
     *
     * @param ownerId 요청자 PK
     * @param code 공개 이벤트 코드
     * @return 소유가 확인된 이벤트 엔티티
     * @throws com.hancome.pulse.common.ApiException 없으면 {@code EVENT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    private Event loadOwnedEvent(Long ownerId, String code) {
        Event event = eventRepository.findByCode(code).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        else return event;
    }

    /**
     * 충돌하지 않는 공개 이벤트 코드를 만든다.
     *
     * @return 기존과 겹치지 않는 코드
     */
    private String generateUniqueCode() {
        while (true) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!eventRepository.existsByCode(code)) return code;
        }
    }
}

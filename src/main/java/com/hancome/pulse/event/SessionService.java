package com.hancome.pulse.event;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.dto.SessionCreateRequest;
import com.hancome.pulse.event.dto.SessionListResponse;
import com.hancome.pulse.event.dto.SessionResponse;
import com.hancome.pulse.event.dto.SessionUpdateRequest;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 CRUD 도메인 로직. 세션은 이벤트의 하위 리소스라 소유권은 부모 이벤트의 {@code owner}로 검증한다. DTO 매핑은 지연 로딩된 {@code
 * event}에 접근하므로 트랜잭션 안(=이 서비스 메서드 내부)에서 끝낸다.
 */
@Service
public class SessionService {
    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;

    public SessionService(EventRepository eventRepository, SessionRepository sessionRepository) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 세션을 생성한다. 상태는 엔티티 기본값 {@code CLOSED}로 시작한다(진행 시점에 소유자가 ACTIVE로 연다).
     *
     * @param req 제목·순서
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 세션이 속할 이벤트의 공개 코드
     * @return 생성된 세션 전체 뷰
     * @throws ApiException 이벤트 없으면 {@code EVENT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    @Transactional
    public SessionResponse create(@NonNull SessionCreateRequest req, Long ownerId, String eventCode) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);

        Session session = new Session(event, req.title(), req.order());
        sessionRepository.save(session);

        return new SessionResponse(
                session.getId(),
                session.getEvent().getId(),
                session.getTitle(),
                session.getOrder(),
                session.getStatus());
    }

    /**
     * 세션을 부분 수정한다(제목·순서·상태). 소유자만 가능. {@code status}는 ACTIVE↔CLOSED 전환(마감/재개)만 허용하며,
     * {@code DELETED}로의 전이는 삭제 엔드포인트 전용이라 거부한다. 이미 삭제된 세션은 수정할 수 없다.
     *
     * @param req 수정할 필드(보낸 것만 반영)
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 세션이 속한 이벤트의 공개 코드
     * @param sessionId 대상 세션 PK
     * @return 수정된 전체 뷰
     * @throws ApiException 세션 없으면 {@code SESSION_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}, 이미 삭제면 {@code
     *     SESSION_ALREADY_DELETED}, status가 {@code DELETED}면 {@code VALIDATION_ERROR}
     */
    @Transactional
    public SessionResponse update(@NonNull SessionUpdateRequest req, Long ownerId, String eventCode, Long sessionId) {
        Session session = loadOwnedSession(ownerId, eventCode, sessionId);
        if (session.getStatus() == SessionStatus.DELETED) throw new ApiException(ErrorCode.SESSION_ALREADY_DELETED);

        if (req.order() != null) {
            session.setOrder(req.order());
        }
        if (req.title() != null) {
            session.setTitle(req.title());
        }
        if (req.status() != null) {
            if (req.status() == SessionStatus.DELETED) throw new ApiException(ErrorCode.VALIDATION_ERROR);
            session.setStatus(req.status());
        }
        sessionRepository.save(session);
        return new SessionResponse(
                session.getId(),
                session.getEvent().getId(),
                session.getTitle(),
                session.getOrder(),
                session.getStatus());
    }

    /**
     * 세션을 소프트 삭제한다({@code status = DELETED}). 소유자만 가능.
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 세션이 속한 이벤트의 공개 코드
     * @param sessionId 대상 세션 PK
     * @throws ApiException 세션 없으면 {@code SESSION_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}, 이미 삭제면 {@code
     *     SESSION_ALREADY_DELETED}
     */
    @Transactional
    public void delete(Long ownerId, String eventCode, Long sessionId) {
        Session session = loadOwnedSession(ownerId, eventCode, sessionId);
        if (session.getStatus() == SessionStatus.DELETED) throw new ApiException(ErrorCode.SESSION_ALREADY_DELETED);
        session.setStatus(SessionStatus.DELETED);
        sessionRepository.save(session);
    }

    /**
     * 이벤트의 공개 세션 목록을 조회한다({@code DELETED} 제외, {@code order} 오름차순). 소유자 검증 없음(공개).
     *
     * @param eventCode 공개 이벤트 코드
     * @return 공개 세션 목록 봉투
     * @throws ApiException 이벤트 없으면 {@code EVENT_NOT_FOUND}
     */
    public SessionListResponse listPublic(String eventCode) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));

        List<Session> sessions =
                sessionRepository.findByEvent_CodeAndStatusNotOrderByOrderAsc(eventCode, SessionStatus.DELETED);
        return SessionListResponse.from(sessions);
    }

    /**
     * 세션을 로드하고 (1) URL의 이벤트 코드에 속한 세션인지, (2) 부모 이벤트의 소유자인지 검증한다(수정·삭제 공통 진입점). 다른 이벤트의
     * 코드가 박힌 URL로 남의 세션에 접근하는 것을 막기 위해 소속 검증을 소유자 검증보다 먼저 한다.
     *
     * @param ownerId 요청자 PK
     * @param eventCode 세션이 속해야 하는 이벤트의 공개 코드
     * @param sessionId 대상 세션 PK
     * @return 소유가 확인된 세션 엔티티
     * @throws ApiException 세션이 없거나 그 이벤트 소속이 아니면 {@code SESSION_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    public Session loadOwnedSession(Long ownerId, String eventCode, Long sessionId) {
        Session session =
                sessionRepository.findById(sessionId).orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getEvent().getCode().equals(eventCode)) throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        if (!session.getEvent().getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        return session;
    }
}

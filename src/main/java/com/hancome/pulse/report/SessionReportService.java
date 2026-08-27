package com.hancome.pulse.report;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.Session;
import com.hancome.pulse.event.SessionRepository;
import com.hancome.pulse.event.SessionStatus;
import com.hancome.pulse.report.dto.SessionReportResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 리포트 생성·조회·리셋. 생성은 비인증(강연자용 링크)이라, 남용을 막는 방어가 인증이 아니라 상태 게이트와 멱등에 있다.
 *
 * <ul>
 *   <li>생성: 세션이 {@code CLOSED}(피드백 마감)일 때만, 세션당 1개(멱등 — GENERATING/GENERATED면 재생성 차단). → 세션당 LLM 1회로 캡.
 *   <li>조회: 세션 피드백 집계가 공개라 링크 보유자 누구나.
 *   <li>리셋: 주최자만(오염된 리포트 회수 → 재생성 가능하게).
 * </ul>
 */
@Service
public class SessionReportService {
    private final SessionRepository sessionRepository;
    private final SessionReportRepository sessionReportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SessionReportService(
            SessionRepository sessionRepository,
            SessionReportRepository sessionReportRepository,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.sessionReportRepository = sessionReportRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 세션 리포트 생성을 시작한다(비인증). 세션이 {@code CLOSED}이고 리포트가 없거나 {@code FAILED}일 때만 가능하다. GENERATING 리포트를
     * 저장하고 커밋 후 워커가 집계·요약을 채운다.
     *
     * @param eventCode 이벤트 공개 코드
     * @param sessionId 세션 PK
     * @param materialSummary 강연자가 프론트에서 요약한 발표 자료(선택, null 가능)
     * @return GENERATING 상태의 세션 리포트 뷰(202)
     * @throws ApiException 세션 없거나 삭제됨/이벤트 불일치면 {@code SESSION_NOT_FOUND}, CLOSED 아니면 {@code
     *     SESSION_NOT_CLOSED}, 이미 생성중·완료면 {@code REPORT_ALREADY_EXISTS}
     */
    @Transactional
    public SessionReportResponse generate(String eventCode, Long sessionId, String materialSummary) {
        Session session = loadSessionInEvent(eventCode, sessionId);
        // CLOSED = 피드백 마감. ACTIVE(진행 중)면 아직 소감이 들어오는 중이라, 지금 생성하면 부분 리포트가 멱등으로 잠긴다.
        if (session.getStatus() != SessionStatus.CLOSED) throw new ApiException(ErrorCode.SESSION_NOT_CLOSED);

        SessionReport report =
                sessionReportRepository.findBySession_Id(sessionId).orElse(null);
        if (report == null) {
            report = new SessionReport(session, materialSummary);
        } else if (report.getStatus() != ReportStatus.FAILED) {
            // GENERATING/GENERATED면 중복 생성 금지(비용·오염 방어). FAILED면 같은 행을 재사용해 재시도한다.
            throw new ApiException(ErrorCode.REPORT_ALREADY_EXISTS);
        } else {
            report.setStatus(ReportStatus.GENERATING);
            report.setMaterialSummary(materialSummary);
            report.setSummaryText(null);
            report.setSentimentBreakdown(null);
            report.setUnclassifiedCount(null);
            report.setTopKeywords(null);
        }
        try {
            sessionReportRepository.saveAndFlush(report); // INSERT 즉시 실행 → session_id UNIQUE 위반을 여기서 잡는다
        } catch (DataIntegrityViolationException e) {
            // 동시 generate 레이스(둘 다 신규 생성)를 중복과 동일하게 매핑(500 대신 409). ReportService.generate와 같은 방식.
            throw new ApiException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        eventPublisher.publishEvent(new SessionReportGenerationRequested(report.getId(), eventCode, sessionId));
        return SessionReportResponse.from(report);
    }

    /**
     * 세션 리포트를 조회한다(공개). 세션 피드백 집계가 공개라 소유자/게스트 구분 없이 같은 뷰를 준다.
     *
     * @param eventCode 이벤트 공개 코드
     * @param sessionId 세션 PK
     * @return 세션 리포트 뷰
     * @throws ApiException 세션 없으면 {@code SESSION_NOT_FOUND}, 리포트 없으면 {@code REPORT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public SessionReportResponse getReport(String eventCode, Long sessionId) {
        Session session = loadSessionInEvent(eventCode, sessionId);
        SessionReport report = sessionReportRepository
                .findBySession_Id(session.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
        return SessionReportResponse.from(report);
    }

    /**
     * 세션 리포트를 회수한다(주최자만). 비인증 생성이라 누가 먼저 오염된 리포트를 만들어 멱등으로 잠겼을 때, 소유자가 지워 재생성을 열어준다.
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 이벤트 공개 코드
     * @param sessionId 세션 PK
     * @throws ApiException 세션 없으면 {@code SESSION_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}, 리포트 없으면 {@code
     *     REPORT_NOT_FOUND}
     */
    @Transactional
    public void reset(Long ownerId, String eventCode, Long sessionId) {
        Session session = loadSessionInEvent(eventCode, sessionId);
        if (!session.getEvent().getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        SessionReport report = sessionReportRepository
                .findBySession_Id(session.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
        sessionReportRepository.delete(report);
    }

    /**
     * 세션을 찾고 이벤트 소속·미삭제를 검증한다. 삭제됐거나 다른 이벤트 소속이면 존재를 숨긴다({@code SESSION_NOT_FOUND}).
     *
     * @param eventCode 이벤트 공개 코드
     * @param sessionId 세션 PK
     * @return 검증된 세션
     */
    private Session loadSessionInEvent(String eventCode, Long sessionId) {
        Session session =
                sessionRepository.findById(sessionId).orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        if (session.getStatus() == SessionStatus.DELETED
                || !session.getEvent().getCode().equals(eventCode)) {
            throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }
}

package com.hancome.pulse.report;

import com.hancome.pulse.common.ApiException;
import com.hancome.pulse.common.ErrorCode;
import com.hancome.pulse.event.Event;
import com.hancome.pulse.event.EventRepository;
import com.hancome.pulse.event.EventStatus;
import com.hancome.pulse.report.dto.PublicReport;
import com.hancome.pulse.report.dto.ReportResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 생성·조회·공개 토글. 생성은 조건(이벤트 ENDED·중복 없음)을 확인해 GENERATING 리포트를 만들고, 실제 채우기는 커밋 후
 * {@link ReportGenerationWorker}가 비동기로 한다(202 반환).
 */
@Service
public class ReportService {
    private final EventRepository eventRepository;
    private final ReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportService(
            EventRepository eventRepository,
            ReportRepository reportRepository,
            ApplicationEventPublisher eventPublisher) {
        this.eventRepository = eventRepository;
        this.reportRepository = reportRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 리포트 생성을 시작한다. 이벤트가 {@code ENDED}이고 리포트가 없거나 {@code FAILED}일 때만 가능하다. GENERATING 리포트를 저장하고
     * 커밋 후 워커가 집계·요약을 채운다.
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 이벤트 공개 코드
     * @return GENERATING 상태의 리포트 뷰(202)
     * @throws ApiException 소유자 아니면 {@code NOT_OWNER}, ENDED 아니면 {@code EVENT_NOT_ENDED}, 이미 생성중·완료면 {@code
     *     REPORT_ALREADY_EXISTS}
     */
    @Transactional
    public ReportResponse generate(Long ownerId, String eventCode) {
        Event event = loadOwnedEvent(ownerId, eventCode);
        if (event.getStatus() != EventStatus.ENDED) throw new ApiException(ErrorCode.EVENT_NOT_ENDED);

        Report report = reportRepository.findByEvent_Code(eventCode).orElse(null);
        if (report == null) {
            report = new Report(event, null, null, null, null, false);
        } else if (report.getStatus() != ReportStatus.FAILED) {
            // GENERATING/GENERATED면 중복 생성 금지. FAILED면 같은 행을 재사용해 재시도한다.
            throw new ApiException(ErrorCode.REPORT_ALREADY_EXISTS);
        } else {
            report.setStatus(ReportStatus.GENERATING);
            report.setSummaryText(null);
            report.setSentimentBreakdown(null);
            report.setUnclassifiedCount(null);
            report.setTopKeywords(null);
        }
        reportRepository.save(report);

        // 커밋 후 워커가 받도록 발행(AFTER_COMMIT). 커밋 전이면 워커가 GENERATING 행을 못 볼 수 있다.
        eventPublisher.publishEvent(new ReportGenerationRequested(report.getId(), eventCode));
        return ReportResponse.from(report);
    }

    /**
     * 리포트를 조회한다. 소유자(인증)는 전체({@link ReportResponse}), 게스트/비소유자는 공개된 경우에만 {@link PublicReport}.
     *
     * @param eventCode 이벤트 공개 코드
     * @param ownerId 인증된 요청자 PK, 게스트면 null
     * @return 소유자면 {@link ReportResponse}, 게스트+공개면 {@link PublicReport}
     * @throws ApiException 리포트 없거나 게스트에게 비공개면 {@code REPORT_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public Object getReport(String eventCode, Long ownerId) {
        Report report = reportRepository
                .findByEvent_Code(eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));

        boolean isOwner =
                ownerId != null && report.getEvent().getOwner().getId().equals(ownerId);
        if (isOwner) return ReportResponse.from(report);
        if (report.isPublic()) return PublicReport.from(report);
        throw new ApiException(ErrorCode.REPORT_NOT_FOUND); // 게스트에게 비공개는 존재를 숨긴다
    }

    /**
     * 리포트 공개 여부를 토글한다. 소유자만 가능.
     *
     * @param ownerId 인증된 주최자 PK
     * @param eventCode 이벤트 공개 코드
     * @param isPublic 공개 여부
     * @return 토글된 리포트 뷰
     * @throws ApiException 리포트 없으면 {@code REPORT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    @Transactional
    public ReportResponse toggle(Long ownerId, String eventCode, boolean isPublic) {
        Report report = reportRepository
                .findByEvent_Code(eventCode)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getEvent().getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        report.setPublic(isPublic);
        return ReportResponse.from(report);
    }

    /**
     * 코드로 이벤트를 찾고 소유자를 검증한다(생성 진입점). EventService의 동명 메서드가 private이라 여기서도 검증한다.
     *
     * @param ownerId 요청자 PK
     * @param eventCode 이벤트 공개 코드
     * @return 소유가 확인된 이벤트
     * @throws ApiException 없으면 {@code EVENT_NOT_FOUND}, 소유자 아니면 {@code NOT_OWNER}
     */
    private Event loadOwnedEvent(Long ownerId, String eventCode) {
        Event event =
                eventRepository.findByCode(eventCode).orElseThrow(() -> new ApiException(ErrorCode.EVENT_NOT_FOUND));
        if (!event.getOwner().getId().equals(ownerId)) throw new ApiException(ErrorCode.NOT_OWNER);
        return event;
    }
}

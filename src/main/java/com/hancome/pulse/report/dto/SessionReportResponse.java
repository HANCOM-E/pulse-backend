package com.hancome.pulse.report.dto;

import com.hancome.pulse.feedback.dto.KeywordCount;
import com.hancome.pulse.feedback.dto.SentimentBreakdown;
import com.hancome.pulse.report.ReportStatus;
import com.hancome.pulse.report.SessionReport;
import java.time.Instant;
import java.util.List;

/**
 * 세션 리포트 뷰. 세션 피드백 집계가 공개라 이 뷰도 링크 보유자에게 공개된다(소유자/게스트 분기 없음). 생성 완료 전(GENERATING)엔 집계·요약 필드가 null이다.
 */
public record SessionReportResponse(
        Long id,
        Long sessionId,
        ReportStatus status,
        String summaryText,
        SentimentBreakdown sentimentBreakdown,
        Integer unclassifiedCount,
        List<KeywordCount> topKeywords,
        String materialSummary,
        Instant generatedAt) {

    /**
     * 엔티티 → 뷰 매핑. {@code session}은 지연 로딩이므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param r 세션 리포트 엔티티
     * @return 뷰 DTO
     */
    public static SessionReportResponse from(SessionReport r) {
        return new SessionReportResponse(
                r.getId(),
                r.getSession().getId(),
                r.getStatus(),
                r.getSummaryText(),
                r.getSentimentBreakdown(),
                r.getUnclassifiedCount(),
                r.getTopKeywords(),
                r.getMaterialSummary(),
                r.getGeneratedAt());
    }
}

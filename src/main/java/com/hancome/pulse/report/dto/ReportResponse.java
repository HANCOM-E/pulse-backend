package com.hancome.pulse.report.dto;

import com.hancome.pulse.feedback.dto.KeywordCount;
import com.hancome.pulse.feedback.dto.SentimentBreakdown;
import com.hancome.pulse.report.Report;
import com.hancome.pulse.report.ReportStatus;
import java.time.Instant;
import java.util.List;

/**
 * 소유자용 리포트 전체 뷰(계약의 {@code Report}). 생성 완료 전(GENERATING)엔 집계·요약 필드가 null이다.
 *
 * <p>게스트 공개 조회에는 {@link PublicReport}를 쓴다.
 */
public record ReportResponse(
        Long id,
        Long eventId,
        ReportStatus status,
        String summaryText,
        SentimentBreakdown sentimentBreakdown,
        Integer unclassifiedCount,
        List<KeywordCount> topKeywords,
        boolean isPublic,
        Instant generatedAt) {

    /**
     * 엔티티 → 전체 뷰 매핑. {@code event}는 지연 로딩이므로 트랜잭션 안에서 호출해야 한다.
     *
     * @param r 리포트 엔티티
     * @return 전체 뷰 DTO
     */
    public static ReportResponse from(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getEvent().getId(),
                r.getStatus(),
                r.getSummaryText(),
                r.getSentimentBreakdown(),
                r.getUnclassifiedCount(),
                r.getTopKeywords(),
                r.isPublic(),
                r.getGeneratedAt());
    }
}

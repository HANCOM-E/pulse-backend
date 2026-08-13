package com.hancome.pulse.report.dto;

import com.hancome.pulse.feedback.dto.KeywordCount;
import com.hancome.pulse.feedback.dto.SentimentBreakdown;
import com.hancome.pulse.report.Report;
import java.util.List;

/** 게스트 공개 리포트 뷰(내부 id·status·isPublic 제외). 공개된({@code isPublic=true}) 리포트만 이 형태로 나간다. */
public record PublicReport(
        String summaryText,
        SentimentBreakdown sentimentBreakdown,
        Integer unclassifiedCount,
        List<KeywordCount> topKeywords) {

    /**
     * @param r 리포트 엔티티
     * @return 게스트 공개 뷰
     */
    public static PublicReport from(Report r) {
        return new PublicReport(
                r.getSummaryText(), r.getSentimentBreakdown(), r.getUnclassifiedCount(), r.getTopKeywords());
    }
}

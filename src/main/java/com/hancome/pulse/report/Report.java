package com.hancome.pulse.report;

import com.hancome.pulse.event.Event;
import com.hancome.pulse.feedback.dto.KeywordCount;
import com.hancome.pulse.feedback.dto.SentimentBreakdown;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", unique = true)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.GENERATING;

    // 생성 완료 전(GENERATING)엔 아래 집계·요약 필드가 비어 있으므로 모두 nullable.
    @Column(columnDefinition = "text")
    private String summaryText;

    // 집계 결과는 ERD 방침대로 JSON 컬럼으로 저장(조회 전용이라 정규화 불필요). 계약 스키마(SentimentBreakdown, KeywordCount[])와 정합.
    // columnDefinition은 생략 — @JdbcTypeCode(JSON)이 dialect별 타입(Postgres jsonb, H2 json)을 알아서 생성해 이식성 유지.
    @JdbcTypeCode(SqlTypes.JSON)
    private SentimentBreakdown sentimentBreakdown;

    private Integer unclassifiedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<KeywordCount> topKeywords;

    private boolean isPublic;

    // 생성 완료(GENERATED) 시각. 워커가 완료 시점에 세팅한다(GENERATING/FAILED엔 null).
    private Instant generatedAt;

    protected Report() {}

    public Report(
            Event event,
            String summaryText,
            SentimentBreakdown sentimentBreakdown,
            Integer unclassifiedCount,
            List<KeywordCount> topKeywords,
            boolean isPublic) {
        this.event = event;
        this.summaryText = summaryText;
        this.sentimentBreakdown = sentimentBreakdown;
        this.unclassifiedCount = unclassifiedCount;
        this.topKeywords = topKeywords;
        this.isPublic = isPublic;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public SentimentBreakdown getSentimentBreakdown() {
        return sentimentBreakdown;
    }

    public void setSentimentBreakdown(SentimentBreakdown sentimentBreakdown) {
        this.sentimentBreakdown = sentimentBreakdown;
    }

    public Integer getUnclassifiedCount() {
        return unclassifiedCount;
    }

    public void setUnclassifiedCount(Integer unclassifiedCount) {
        this.unclassifiedCount = unclassifiedCount;
    }

    public List<KeywordCount> getTopKeywords() {
        return topKeywords;
    }

    public void setTopKeywords(List<KeywordCount> topKeywords) {
        this.topKeywords = topKeywords;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}

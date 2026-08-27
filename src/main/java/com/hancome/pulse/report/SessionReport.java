package com.hancome.pulse.report;

import com.hancome.pulse.event.Session;
import com.hancome.pulse.feedback.dto.KeywordCount;
import com.hancome.pulse.feedback.dto.SentimentBreakdown;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 세션 단위 리포트. 이벤트 단위 {@link Report}와 별도 테이블로, 세션당 1개(session_id UNIQUE)다. 집계·요약 필드는 {@link Report}와
 * 동형(같은 값 타입 재사용)이고, 추가로 강연자가 올린 발표 자료 요약({@code materialSummary})을 담는다 — 이 값은 세션 리포트 요약의 근거이자,
 * 이벤트 리포트가 전 세션 자료를 그러모아 요약할 때의 입력이기도 하다.
 *
 * <p>공개 토글은 두지 않는다 — 세션 피드백 집계 자체가 공개(대시보드)라, 그 요약인 세션 리포트도 링크 보유자에게 공개다.
 */
@Entity
@Table(name = "session_reports")
public class SessionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", unique = true)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.GENERATING;

    // 강연자가 프론트에서 자료를 LLM 요약한 결과. 생성 요청 시 저장하고, 완료 후에도 남겨 이벤트 리포트 융합에 재사용한다.
    @Column(columnDefinition = "text")
    private String materialSummary;

    // 생성 완료 전(GENERATING)엔 아래 집계·요약 필드가 비어 있으므로 모두 nullable.
    @Column(columnDefinition = "text")
    private String summaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    private SentimentBreakdown sentimentBreakdown;

    private Integer unclassifiedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<KeywordCount> topKeywords;

    // 생성 완료(GENERATED) 시각. 워커가 완료 시점에 세팅한다(GENERATING/FAILED엔 null).
    private Instant generatedAt;

    protected SessionReport() {}

    public SessionReport(Session session, String materialSummary) {
        this.session = session;
        this.materialSummary = materialSummary;
    }

    public Long getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getMaterialSummary() {
        return materialSummary;
    }

    public void setMaterialSummary(String materialSummary) {
        this.materialSummary = materialSummary;
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

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}

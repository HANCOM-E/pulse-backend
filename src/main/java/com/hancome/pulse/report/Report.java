package com.hancome.pulse.report;

import com.hancome.pulse.event.Event;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

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
    private ReportStatus status = ReportStatus.PENDING;

    @Column(columnDefinition = "text")
    private String summaryText;

    @Column(columnDefinition = "text")
    private String sentimentBreakdown;

    @ElementCollection
    private List<String> topKeywords;

    private boolean isPublic;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant generatedAt;

    protected Report() {}

    public Report(
            Event event, String summaryText, String sentimentBreakdown, List<String> topKeywords, boolean isPublic) {
        this.event = event;
        this.summaryText = summaryText;
        this.sentimentBreakdown = sentimentBreakdown;
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

    public String getSentimentBreakdown() {
        return sentimentBreakdown;
    }

    public void setSentimentBreakdown(String sentimentBreakdown) {
        this.sentimentBreakdown = sentimentBreakdown;
    }

    public List<String> getTopKeywords() {
        return topKeywords;
    }

    public void setTopKeywords(List<String> topKeywords) {
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
}

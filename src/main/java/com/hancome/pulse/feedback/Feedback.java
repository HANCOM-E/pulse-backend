package com.hancome.pulse.feedback;

import com.hancome.pulse.event.Session;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private Session session;

    @Column(columnDefinition = "text", nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment sentiment;

    private boolean toxic = false;

    // 이 소감이 어떤 버전의 클라이언트 태깅 모델로 판단됐는지. 계약상 항상 전송되며 없으면 서버가 거절.
    @Column(nullable = false)
    private String taggerVersion;

    @ElementCollection
    private List<String> keywords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackStatus status = FeedbackStatus.VISIBLE;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected Feedback() {}

    public Feedback(
            Session session,
            String text,
            Sentiment sentiment,
            boolean toxic,
            String taggerVersion,
            List<String> keywords) {
        this.session = session;
        this.text = text;
        this.sentiment = sentiment;
        this.toxic = toxic;
        this.taggerVersion = taggerVersion;
        this.keywords = keywords;
    }

    public Long getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public boolean getToxic() {
        return toxic;
    }

    public void setToxic(boolean toxic) {
        this.toxic = toxic;
    }

    public String getTaggerVersion() {
        return taggerVersion;
    }

    public void setTaggerVersion(String taggerVersion) {
        this.taggerVersion = taggerVersion;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public void setStatus(FeedbackStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

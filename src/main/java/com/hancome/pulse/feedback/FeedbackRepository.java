package com.hancome.pulse.feedback;

import com.hancome.pulse.feedback.dto.KeywordCount;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 소감 저장·집계·레이트리밋 조회.
 *
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    @Query("""
        select f.sentiment, count(f)
        from Feedback f
        where f.session.event.code = :eventCode
            and ( :sessionId is null or f.session.id = :sessionId)
            and f.status = :status
        group by f.sentiment
""")
    List<Object[]> countBySentiment(
            @Param("eventCode") String eventCode,
            @Param("sessionId") Long sessionId,
            @Param("status") FeedbackStatus status);

    @Query("""
        select new com.hancome.pulse.feedback.dto.KeywordCount(k, cast(count(k) as integer))
        from Feedback f join f.keywords k
        where f.session.event.code = :eventCode
            and ( :sessionId is null or f.session.id = :sessionId)
            and f.status = :status
        group by k
        order by count(k) desc
""")
    List<KeywordCount> topKeywords(
            @Param("eventCode") String EventCode,
            @Param("sessionId") Long sessionId,
            @Param("status") FeedbackStatus status,
            Pageable pageable);

    @Query("""
        select f
        from Feedback f
        where f.session.event.code = :eventCode
            and (:sessionId is null or f.session.id = :sessionId)
            and f.status = :status
        order by f.createdAt desc
""")
    List<Feedback> recentFeedbacks(
            @Param("eventCode") String eventCode,
            @Param("sessionId") Long sessionId,
            @Param("status") FeedbackStatus status,
            Pageable pageable);
}

package com.hancome.pulse.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 세션 리포트 조회·저장. 세션당 1개(session_id UNIQUE). */
public interface SessionReportRepository extends JpaRepository<SessionReport, Long> {

    /**
     * 세션 PK로 리포트를 찾는다(생성 멱등 검사·조회·리셋 공통 진입점).
     *
     * @param sessionId 세션 PK
     * @return 세션 리포트(없으면 empty)
     */
    Optional<SessionReport> findBySession_Id(Long sessionId);

    /**
     * 이벤트의 세션 리포트 전부를 찾는다(이벤트 리포트가 전 세션 자료요약을 그러모을 때 사용).
     *
     * @param eventCode 이벤트 공개 코드
     * @return 그 이벤트에 속한 세션 리포트들
     */
    List<SessionReport> findBySession_Event_Code(String eventCode);
}

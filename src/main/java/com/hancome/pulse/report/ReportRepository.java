package com.hancome.pulse.report;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 리포트 조회·저장. 이벤트당 1개(event_id UNIQUE)라 공개 코드로 단건 조회한다. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 이벤트 공개 코드로 리포트를 찾는다(생성/조회/토글 공통 진입점).
     *
     * @param eventCode 이벤트 공개 코드
     * @return 리포트(없으면 empty)
     */
    Optional<Report> findByEvent_Code(String eventCode);
}

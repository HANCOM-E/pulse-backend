package com.hancome.pulse.report.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 리포트 공개 여부 토글 요청.
 *
 * @param isPublic 공개(true)/비공개(false)
 */
public record ReportPublishRequest(@NotNull Boolean isPublic) {}

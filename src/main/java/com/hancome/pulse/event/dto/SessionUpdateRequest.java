package com.hancome.pulse.event.dto;

import com.hancome.pulse.event.SessionStatus;
import jakarta.validation.constraints.Pattern;

/**
 * 세션 부분 수정 요청. 모든 필드 optional(보낸 것만 반영).
 *
 * <p>{@code status}로는 마감/재개({@code ACTIVE}↔{@code CLOSED})만 허용한다. {@code DELETED}로의 전이는 DELETE
 * 엔드포인트로 하며, 잘못된 값은 서비스에서 막는다.
 */
public record SessionUpdateRequest(
        @Pattern(regexp = "(?U).*\\S.*", message = "제목은 공백만일 수 없습니다")
        String title,

        Integer order,
        SessionStatus status) {}

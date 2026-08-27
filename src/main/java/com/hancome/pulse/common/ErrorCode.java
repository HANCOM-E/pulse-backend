package com.hancome.pulse.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 인증/인가
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"), // 400
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"), // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"), // 401
    NOT_OWNER(HttpStatus.FORBIDDEN, "리소스의 소유자가 아닙니다"), // 403
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "CSRF 토큰이 없거나 유효하지 않습니다"), // 403

    // 404 — 리소스별
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이벤트입니다"), // 404
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 세션입니다"), // 404
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 소감입니다"), // 404
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트가 없거나 비공개입니다"), // 404
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다"), // 404

    // 409 — 충돌/상태
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"), // 409
    EVENT_NOT_LIVE(HttpStatus.CONFLICT, "이벤트가 LIVE 상태가 아닙니다"), // 409
    INVALID_EVENT_STATE_TRANSITION(HttpStatus.CONFLICT, "유효하지 않은 상태 전이입니다"), // 409
    EVENT_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 이벤트입니다"), // 409
    FEEDBACK_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 소감입니다"), // 409
    SESSION_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 세션입니다"), // 409
    SESSION_CLOSED(HttpStatus.CONFLICT, "세션이 마감되어 소감을 받지 않습니다"), // 409
    SESSION_NOT_CLOSED(HttpStatus.CONFLICT, "세션이 마감(CLOSED)되지 않아 리포트를 생성할 수 없습니다"), // 409
    EVENT_NOT_ENDED(HttpStatus.CONFLICT, "이벤트가 종료되지 않았습니다"), // 409
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "리포트가 이미 생성 중이거나 완료되었습니다"), // 409
    GAME_NOT_OPEN(HttpStatus.CONFLICT, "게임이 참가 가능한 상태(OPEN)가 아닙니다"), // 409
    INVALID_GAME_STATE_TRANSITION(HttpStatus.CONFLICT, "유효하지 않은 게임 상태 전이입니다"), // 409
    GAME_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 종료된 게임입니다"), // 409

    // 기타
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "제출 빈도를 초과했습니다"), // 429
    REPORT_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "리포트 생성(LLM 호출)에 실패했습니다"), // 502
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"); // 500
    private final HttpStatus status;
    private final String defaultMessage;

    /**
     * @param status 이 코드가 응답할 HTTP 상태
     * @param defaultMessage 별도 메시지가 없을 때 봉투에 담을 기본 문구
     */
    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /**
     * @return 이 코드에 매핑된 HTTP 상태
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * @return 봉투 message에 쓸 기본 문구
     */
    public String defaultMessage() {
        return defaultMessage;
    }
}

package com.hancome.pulse.common;

/**
 * 도메인/애플리케이션 예외. {@link ErrorCode}를 담고 있어 전역 핸들러가 상태·봉투로 변환한다.
 *
 * <p>비즈니스 실패를 unchecked로 던지고 {@link GlobalExceptionHandler}가 한 곳에서 잡는다.
 */
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    /**
     * @param errorCode 이 예외가 나타내는 에러 코드(상태·기본 메시지 포함)
     */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * @return 이 예외에 담긴 에러 코드
     */
    public ErrorCode errorCode() {
        return errorCode;
    }
}

package com.hancome.pulse.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 컨트롤러/서비스에서 올라온 예외를 팀 API 명세서의 공통 봉투({@code {code, message}})로 변환한다.
 *
 * <p>시큐리티 필터 단에서 나는 인증/인가 실패(401/403)는 여기까지 오지 않으므로 {@link SecurityConfig}에서 직접 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 우리가 의도적으로 던진 도메인 예외를 그 {@link ErrorCode}에 정의된 상태/메시지로 변환한다.
     *
     * @param e 발생한 도메인 예외
     * @return 코드에 매핑된 상태와 {@code {code, message}} 봉투
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        ErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), e.getMessage()));
    }

    /**
     * {@code @Valid} 검증 실패를 400 {@code VALIDATION_ERROR}로 변환한다. 메시지는 첫 번째 필드 에러를 담는다.
     *
     * @param e 검증 실패 예외
     * @return 400 상태와 "필드: 사유" 형태의 메시지 봉투
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status())
                .body(new ErrorResponse(
                        code.name(),
                        e.getBindingResult().getFieldErrors().stream()
                                .findFirst()
                                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                                .orElse(code.defaultMessage())));
    }

    /**
     * 읽을 수 없는 요청 본문(깨진 JSON, 타입 불일치 등)을 400 {@code VALIDATION_ERROR}로 변환한다.
     *
     * <p>Jackson 역직렬화는 {@code @Valid} 이전에 실행되므로, 이 예외를 잡지 않으면 클라이언트 실수가 500으로 새어 나간다.
     *
     * @param e 본문 파싱 실패 예외
     * @return 400 상태와 봉투
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), "요청 본문을 읽을 수 없습니다"));
    }

    /**
     * SSE 클라이언트가 연결을 끊은 뒤(탭 닫기·이탈·새로고침) 서버가 그 죽은 async 커넥션에 쓰려다 나는 예외.
     *
     * <p>응답할 대상이 이미 없으므로 조용히 삼킨다(void 반환 → 바디 미작성). 잡지 않으면 아래 catch-all이 받아
     * {@code ErrorResponse}(JSON)를 {@code text/event-stream} 응답에 쓰려다 2차 예외까지 내며 로그를 오염시킨다. 구독자
     * 정리는 {@code SseHub}의 {@code onError/onTimeout}이 담당하므로 여기선 DEBUG 로그만 남긴다.
     *
     * @param e 클라이언트 연결 끊김 알림 예외
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException e) {
        log.debug("[SSE] client disconnected: {}", e.getMessage());
    }

    /**
     * 예상하지 못한 나머지 예외를 500 {@code INTERNAL_ERROR}로 변환한다. 내부 예외 메시지는 노출하지 않는다.
     *
     * @param e 처리되지 않은 예외
     * @return 500 상태와 고정 메시지 봉투
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 스택트레이스는 서버 로그에만 남기고, 클라이언트엔 내부 정보 없는 고정 메시지만 준다.
        log.error("처리되지 않은 예외", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), code.defaultMessage()));
    }
}

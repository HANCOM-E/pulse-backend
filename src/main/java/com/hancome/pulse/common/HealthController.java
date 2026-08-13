package com.hancome.pulse.common;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Operation(security = {}) // 공개 헬스체크 — 전역 cookieAuth 요구 해제
    @GetMapping("/api/v1/health")
    public HealthResponse healthCheck() {
        return new HealthResponse("ok");
    }

    record HealthResponse(String status) {}
}

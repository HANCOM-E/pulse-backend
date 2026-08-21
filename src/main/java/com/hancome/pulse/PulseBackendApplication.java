package com.hancome.pulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync // 리포트 생성 워커·SSE 브로드캐스트(@Async)를 별 스레드로 실행
@EnableScheduling // SSE 하트비트(@Scheduled)
public class PulseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseBackendApplication.class, args);
    }
}

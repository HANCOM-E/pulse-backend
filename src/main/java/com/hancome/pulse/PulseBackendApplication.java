package com.hancome.pulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // 리포트 생성 워커(@Async)를 별 스레드로 실행
public class PulseBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseBackendApplication.class, args);
    }
}

package com.hancome.pulse.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // REST API라 CSRF 토큰 불필요
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/health")
                        .permitAll() // 헬스체크만 공개
                        .anyRequest()
                        .authenticated()); // 나머지는 인증 필요 (0-3에서 JWT 필터 추가)
        return http.build();
    }
}

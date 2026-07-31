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
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // 0-1: 일단 전부 열기 (0-3에서 실제 규칙으로 교체)
        return http.build();
    }
}

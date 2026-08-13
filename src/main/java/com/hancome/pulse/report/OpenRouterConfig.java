package com.hancome.pulse.report;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter 호출용 {@link RestClient} 빈. read timeout은 properties(env)로 주입해 재배포 없이 조정한다. connect timeout은 바꿀 일이
 * 드물어 리터럴로 둔다. read timeout 초과 시 {@code ResourceAccessException}(RuntimeException)이 나 워커가 리포트를 {@code
 * FAILED}로 확정한다.
 */
@Configuration
class OpenRouterConfig {

    @Bean
    RestClient openRouterClient(
            @Value("${openrouter.api-key}") String apiKey, @Value("${openrouter.read-timeout}") Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(ReportSummaryGenerator.OPENROUTER_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();
    }
}

package com.lina.bff.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : RAG Pipeline / AI Agent 서버 호출용 동기 RestClient 빈 정의.
 *           lina.rag.* / lina.ai-agent.* 설정값에서 base-url 과 timeout 을 주입한다.
 *           SSE 스트리밍은 Feature 5 의 RagClient 가 HttpClient InputStream 으로 별도 파싱한다
 *           (WebFlux/Mono/Flux 사용 금지 — backend/CLAUDE.md §2.1 / §6).
 * 작성일 : 2026-05-21
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-21, 최초 작성, 2단계 Feature 2 — 동기 RestClient 빈 등록
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — Virtual Threads 가 I/O 블로킹 흡수
 *   - Spring Boot 3.3.x / Spring Web 6.1.x (RestClient)
 * --------------------------------------------------
 * </pre>
 */
@Configuration
public class RagClientConfig {

  @Bean
  RestClient ragRestClient(
      @Value("${lina.rag.base-url:}") String baseUrl,
      @Value("${lina.rag.request-timeout-ms:30000}") long requestTimeoutMs) {
    return buildClient(baseUrl, requestTimeoutMs);
  }

  @Bean
  RestClient aiAgentRestClient(
      @Value("${lina.ai-agent.base-url:}") String baseUrl,
      @Value("${lina.ai-agent.request-timeout-ms:30000}") long requestTimeoutMs) {
    return buildClient(baseUrl, requestTimeoutMs);
  }

  private static RestClient buildClient(String baseUrl, long requestTimeoutMs) {
    Duration timeout = Duration.ofMillis(requestTimeoutMs);
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
    ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder = builder.baseUrl(baseUrl);
    }
    return builder.build();
  }
}

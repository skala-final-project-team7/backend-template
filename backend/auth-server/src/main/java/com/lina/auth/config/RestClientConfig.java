package com.lina.auth.config;

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
 * 작성목적 : Atlassian 외부 호출용 RestClient. 엔드포인트가 auth.atlassian.com/api.atlassian.com 으로
 *           나뉘므로 base-url 없이 절대 URI(OAuthProperties)로 호출한다. 타임아웃은 운영 파라미터.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 최초 작성, 3단계 Feature 3 — OAuth Authorization Code Flow
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x (RestClient + Virtual Threads 동기 I/O)
 * --------------------------------------------------
 * </pre>
 */
@Configuration
public class RestClientConfig {

  @Bean
  RestClient atlassianRestClient(
      @Value("${lina.oauth.confluence.request-timeout-ms:10000}") long requestTimeoutMs) {
    Duration timeout = Duration.ofMillis(requestTimeoutMs);
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
    ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}

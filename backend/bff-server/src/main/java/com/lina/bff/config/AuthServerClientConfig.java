package com.lina.bff.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthServerClientConfig {

  // auth-server /internal/** 가 내부 호출자 인증에 요구하는 헤더 이름(ML 팀과 합의한 고정 값).
  private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

  @Bean
  RestClient authServerRestClient(
      @Value("${lina.auth-server.base-url:}") String baseUrl,
      @Value("${lina.auth-server.request-timeout-ms:30000}") long requestTimeoutMs,
      @Value("${lina.auth-server.internal-api-key:}") String internalApiKey) {
    Duration timeout = Duration.ofMillis(requestTimeoutMs);
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder = builder.baseUrl(baseUrl);
    }
    // /internal/admin/** 호출(Admin Key activate/deactivate)은 이 헤더가 있어야 인증을 통과한다.
    if (internalApiKey != null && !internalApiKey.isBlank()) {
      builder = builder.defaultHeader(INTERNAL_API_KEY_HEADER, internalApiKey);
    }
    return builder.build();
  }
}

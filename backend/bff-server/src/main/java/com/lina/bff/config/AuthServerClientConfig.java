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

  @Bean
  RestClient authServerRestClient(
      @Value("${lina.auth-server.base-url:}") String baseUrl,
      @Value("${lina.auth-server.request-timeout-ms:30000}") long requestTimeoutMs) {
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
    return builder.build();
  }
}

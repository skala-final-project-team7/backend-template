package com.lina.bff.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthServerClientConfig {

  @Bean
  RestClient authServerRestClient(
      @Value("${lina.auth-server.base-url:}") String baseUrl,
      @Value("${lina.auth-server.request-timeout-ms:30000}") long requestTimeoutMs) {
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

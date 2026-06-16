package com.lina.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Feature 7 — actuator 민감 엔드포인트(env/heapdump/threaddump/beans)가 외부로 노출되지 않으면서, k8s
 * liveness/readiness 헬스체크는 정상 동작함을 실제 기동(RANDOM_PORT)으로 검증한다.
 *
 * <p>노출 차단이 헬스체크/모니터링까지 깨뜨리지 않는지 함께 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuatorEndpointExposureTest {

  private static final String[] SENSITIVE_ENDPOINTS = {
    "/actuator/env", "/actuator/heapdump", "/actuator/threaddump", "/actuator/beans"
  };

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("/actuator/health 는 200 으로 동작한다 (헬스체크가 깨지지 않는다)")
  void healthEndpointIsUp() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
  }

  @Test
  @DisplayName("k8s liveness/readiness probe 는 200 으로 동작한다")
  void healthProbesAreUp() {
    assertThat(restTemplate.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("모니터링 엔드포인트(metrics)는 계속 200 으로 노출된다")
  void monitoringEndpointsStayExposed() {
    // prometheus 는 runtimeOnly 레지스트리라 테스트 컨텍스트에는 미등록 — 운영 런타임 전용.
    // 여기서는 허용목록이 유지돼 모니터링 엔드포인트가 막히지 않는지를 metrics 로 확인한다.
    assertThat(restTemplate.getForEntity("/actuator/metrics", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("민감 엔드포인트(env/heapdump/threaddump/beans)는 노출되지 않는다 (2xx 아님)")
  void sensitiveEndpointsAreNotExposed() {
    for (String path : SENSITIVE_ENDPOINTS) {
      ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
      assertThat(response.getStatusCode().is2xxSuccessful())
          .as("%s 는 노출되면 안 된다 (status=%s)", path, response.getStatusCode())
          .isFalse();
    }
  }
}

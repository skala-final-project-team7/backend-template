package com.lina.bff.rag.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.lina.bff.rag.client.RagClient.RagClientException;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class RagClientTest {

  @RegisterExtension static WireMockExtension wireMock = WireMockExtension.newInstance().build();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("ML SSE 스트림을 동기 RestClient로 호출하고 event/data 쌍을 순서대로 파싱한다")
  void shouldParseSseEventsFromMlQuery() {
    wireMock.stubFor(
        post(urlEqualTo("/ml/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                    .withBody(
                        """
                        event: status
                        data: {"phase":"searching","message":"검색 중"}

                        event: token
                        data: {"content":"S3 권한 오류는"}

                        event: done
                        data: {}

                        """)));

    RagClient ragClient = new RagClient(buildRestClient(), objectMapper);
    RagQueryCommand command =
        new RagQueryCommand(
            "지난번 S3 버킷 권한 오류 때 어떻게 해결했어?",
            "user-001",
            List.of("Cloud-Control-Center"),
            "conv-uuid-001",
            List.of(new RagQueryCommand.HistoryMessage("user", "S3 관련 장애 이력 알려줘")),
            true);

    List<RagSseEvent> events = new ArrayList<>();
    ragClient.streamQuery(command, events::add);

    assertThat(events).extracting(RagSseEvent::event).containsExactly("status", "token", "done");
    assertThat(events.get(0).data().get("phase").asText()).isEqualTo("searching");
    assertThat(events.get(1).data().get("content").asText()).isEqualTo("S3 권한 오류는");
  }

  @Test
  @DisplayName("/ml/query 요청 본문은 camelCase 계약을 따르고 accessToken/cloudId 를 포함하지 않는다")
  void shouldSendQueryBodyWithoutConfluenceCredentials() {
    wireMock.stubFor(
        post(urlEqualTo("/ml/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(
                        """
                        event: done
                        data: {}

                        """)));

    RagClient ragClient = new RagClient(buildRestClient(), objectMapper);
    RagQueryCommand command =
        new RagQueryCommand(
            "질문",
            "user-001",
            List.of("Cloud-Control-Center"),
            "conv-uuid-001",
            List.of(new RagQueryCommand.HistoryMessage("assistant", "이전 답변")),
            true);

    ragClient.streamQuery(command, event -> {});

    wireMock.verify(
        postRequestedFor(urlEqualTo("/ml/query"))
            .withHeader(
                "Content-Type",
                com.github.tomakehurst.wiremock.client.WireMock.containing(
                    MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "question": "질문",
                      "userId": "user-001",
                      "groups": ["Cloud-Control-Center"],
                      "conversationId": "conv-uuid-001",
                      "history": [
                        {"role": "assistant", "content": "이전 답변"}
                      ],
                      "stream": true
                    }
                    """,
                    true,
                    true)));

    String requestBody = wireMock.getAllServeEvents().get(0).getRequest().getBodyAsString();
    assertThat(requestBody).doesNotContain("accessToken", "cloudId");
  }

  @Test
  @DisplayName("ML 서버가 5xx 를 반환하면 ML_SERVER_ERROR 로 실패한다")
  void shouldFailWithMlServerErrorWhenMlReturns5xx() {
    wireMock.stubFor(
        post(urlEqualTo("/ml/query"))
            .willReturn(aResponse().withStatus(500).withBody("internal error")));
    RagClient ragClient = new RagClient(buildRestClient(), objectMapper);

    assertThatThrownBy(() -> ragClient.streamQuery(command(), event -> {}))
        .isInstanceOf(RagClientException.class)
        .extracting("errorCode")
        .isEqualTo(RagClient.ML_SERVER_ERROR);
  }

  @Test
  @DisplayName("ML SSE idle timeout 이 발생하면 ML_TIMEOUT 으로 실패한다")
  void shouldFailWithMlTimeoutWhenReadTimeoutExceeded() {
    wireMock.stubFor(
        post(urlEqualTo("/ml/query"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withFixedDelay(200)
                    .withBody(
                        """
                        event: done
                        data: {}

                        """)));
    RagClient ragClient = new RagClient(buildRestClient(Duration.ofMillis(50)), objectMapper);

    assertThatThrownBy(() -> ragClient.streamQuery(command(), event -> {}))
        .isInstanceOf(RagClientException.class)
        .extracting("errorCode")
        .isEqualTo(RagClient.ML_TIMEOUT);
  }

  private RestClient buildRestClient() {
    return buildRestClient(Duration.ofSeconds(30));
  }

  private RestClient buildRestClient(Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setReadTimeout(readTimeout);
    return RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(requestFactory).build();
  }

  private RagQueryCommand command() {
    return new RagQueryCommand(
        "질문", "user-001", List.of("Cloud-Control-Center"), "conv-uuid-001", List.of(), true);
  }
}

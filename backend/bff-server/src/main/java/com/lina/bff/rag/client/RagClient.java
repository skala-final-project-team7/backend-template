package com.lina.bff.rag.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : RAG Pipeline /ml/query 호출 전담 Client.
 *           Spring MVC + Virtual Threads 원칙에 맞춰 동기 RestClient 와 InputStream 파싱으로
 *           ML 서버의 SSE event/data 스트림을 순서대로 소비자에게 전달한다.
 * 작성일 : 2026-06-05
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-05, 최초 작성, 2단계 Feature 5 RAG Client SSE 파서 구현
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — Virtual Threads 가 I/O 블로킹 흡수
 *   - Spring Boot 3.3.x / Spring Web 6.1.x (RestClient)
 * --------------------------------------------------
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class RagClient {

  private final RestClient ragRestClient;
  private final ObjectMapper objectMapper;

  /**
   * RAG Pipeline 의 `POST /ml/query` 엔드포인트를 호출해 ML 서버가 반환하는 SSE 스트림을 읽는다.
   *
   * <p>이 메소드는 외부 HTTP 호출 경계를 담당한다. Service 계층이 ML 서버 URL, HTTP status, InputStream 처리 방식을 직접 알지 않도록
   * 숨기고, 파싱된 `event`/`data` 쌍만 `eventConsumer` 로 전달한다. `done` 이벤트의 messageId 보강, `error` 이벤트 중계,
   * assistant 메시지 저장 같은 BFF boundary 가공은 이 메소드가 아니라 호출자인 ChatService 책임이다.
   *
   * <p>입력은 `RagQueryCommand` 이며 `/ml/query` request body 로 그대로 직렬화된다. 포함 필드는 사용자 질문(`question`),
   * ACL(`userId`, `groups`), 대화 식별자(`conversationId`), 최근 대화 이력(`history`), 스트리밍
   * 플래그(`stream=true`)다. Confluence credential(`accessToken`, `cloudId`) 은 이 요청에 포함하지 않는다.
   *
   * <p>출력은 HTTP 응답 하나가 아니라 SSE 이벤트 스트림이다. 이 메소드는 ML 서버가 내려주는 `status`, `token`, `sources`,
   * `verification`, `meta`, `done`, `error` 이벤트를 `RagSseEvent(event, data)` 형태로 순서대로 전달한다. 각 이벤트의
   * JSON payload 는 `JsonNode` 로 보존한다.
   *
   * @param command RAG 질의 요청. userId/groups ACL 과 stream=true 를 포함해야 한다.
   * @param eventConsumer 파싱된 SSE 이벤트 소비자
   * @throws RagClientException ML 호출 또는 SSE 파싱 실패 시
   */
  public void streamQuery(RagQueryCommand command, Consumer<RagSseEvent> eventConsumer) {
    ragRestClient
        .post()
        .uri("/ml/query")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .body(command)
        .exchange(
            (request, response) -> {
              if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RagClientException("RAG Pipeline returned non-success status");
              }

              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                parseSse(reader, eventConsumer);
              } catch (IOException exception) {
                throw new RagClientException("Failed to read RAG SSE stream", exception);
              }
              return null;
            });
  }

  /**
   * ML 서버 응답 본문을 SSE 라인 단위로 해석한다.
   *
   * <p>SSE 메시지는 빈 줄을 기준으로 하나의 이벤트가 끝난다. 이 메소드는 `event:` 라인과 `data:` 라인을 모아 이벤트 단위로 분리하고, 각 이벤트의
   * payload 해석은 `publishEvent` 에 위임한다. 알 수 없는 SSE 필드는 현재 계약에 필요하지 않으므로 무시한다.
   */
  private void parseSse(BufferedReader reader, Consumer<RagSseEvent> eventConsumer)
      throws IOException {
    String eventName = null;
    StringBuilder data = new StringBuilder();

    String line;
    while ((line = reader.readLine()) != null) {
      if (line.isEmpty()) {
        publishEvent(eventName, data, eventConsumer);
        eventName = null;
        data.setLength(0);
        continue;
      }
      if (line.startsWith("event:")) {
        eventName = line.substring("event:".length()).trim();
        continue;
      }
      if (line.startsWith("data:")) {
        if (!data.isEmpty()) {
          data.append('\n');
        }
        data.append(line.substring("data:".length()).trim());
      }
    }

    publishEvent(eventName, data, eventConsumer);
  }

  /**
   * 파싱이 끝난 SSE 이벤트를 BFF 내부 DTO 로 변환해 호출자에게 전달한다.
   *
   * <p>이 메소드는 data 문자열을 JSON 으로 파싱해 원본 payload 구조를 `JsonNode` 로 보존한다. 이벤트 이름이 없으면 불완전한 이벤트로 보고 전달하지
   * 않는다.
   */
  private void publishEvent(
      String eventName, StringBuilder data, Consumer<RagSseEvent> eventConsumer) {
    if (eventName == null || eventName.isBlank()) {
      return;
    }

    try {
      JsonNode payload =
          data.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(data.toString());
      eventConsumer.accept(new RagSseEvent(eventName, payload));
    } catch (JsonProcessingException exception) {
      throw new RagClientException("Failed to parse RAG SSE event data", exception);
    }
  }

  public static class RagClientException extends RuntimeException {

    public RagClientException(String message) {
      super(message);
    }

    public RagClientException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

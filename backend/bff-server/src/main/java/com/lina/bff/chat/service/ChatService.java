package com.lina.bff.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.entity.VerificationResult;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.rag.client.RagClient;
import com.lina.bff.rag.client.RagClient.RagClientException;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import com.lina.common.exception.ErrorCode;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : BFF 채팅 질의 서비스.
 *           현재 단계에서는 RAG Pipeline /ml/query 호출에 필요한 question/history/ACL/stream 입력을 조립하고
 *           RagClient 로 위임한다. SseEmitter 외부 중계와 메시지 영속 처리는 Feature 5 후속 체크리스트에서 확장한다.
 * 작성일 : 2026-06-05
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-05, 최초 작성, 2단계 Feature 5 RAG 호출 입력 조립 구현
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — Virtual Threads 기반 동기 I/O
 *   - Spring Boot 3.3.x / Spring MVC 6.1.x 기준
 * --------------------------------------------------
 * </pre>
 */
@Service
public class ChatService {

  private static final Set<String> RELAYABLE_EVENTS =
      Set.of("status", "token", "sources", "verification", "meta", "done", "error");
  private static final Set<String> RELAYABLE_ERROR_CODES =
      Set.of(
          RagClient.ML_SERVER_ERROR,
          RagClient.ML_TIMEOUT,
          RagClient.ML_CONNECTION_ERROR,
          ErrorCode.UNAUTHORIZED.getCode());
  private static final String DONE_EVENT = "done";
  private static final String ERROR_EVENT = "error";
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ChatMessagePersistenceService chatMessagePersistenceService;
  private final CurrentUserProvider currentUserProvider;
  private final RagClient ragClient;
  private final TaskExecutor chatSseTaskExecutor;
  private final int historyTurns;
  private final long sseTimeoutMs;

  public ChatService(
      ChatMessagePersistenceService chatMessagePersistenceService,
      CurrentUserProvider currentUserProvider,
      RagClient ragClient,
      @Qualifier("chatSseTaskExecutor") TaskExecutor chatSseTaskExecutor,
      @Value("${lina.rag.history-turns:10}") int historyTurns,
      @Value("${lina.rag.sse-timeout-ms:60000}") long sseTimeoutMs) {
    this.chatMessagePersistenceService = chatMessagePersistenceService;
    this.currentUserProvider = currentUserProvider;
    this.ragClient = ragClient;
    this.chatSseTaskExecutor = chatSseTaskExecutor;
    this.historyTurns = historyTurns;
    this.sseTimeoutMs = sseTimeoutMs;
  }

  /**
   * FE 로 반환할 SSE 연결을 생성하고 RAG 질의 중계를 시작한다.
   *
   * @param conversationId 질의가 속한 대화 식별자
   * @param question 사용자 질문
   * @return SseEmitter — 공통 Wrapper 를 적용하지 않는 SSE 응답 객체
   */
  public SseEmitter streamChat(String conversationId, String question) {
    SseEmitter emitter = new SseEmitter(sseTimeoutMs);
    AtomicBoolean terminalEventSent = new AtomicBoolean(false);
    emitter.onTimeout(
        () -> sendMlErrorThenComplete(emitter, terminalEventSent, RagClient.ML_TIMEOUT));
    chatSseTaskExecutor.execute(
        () -> {
          try {
            relayRagQuery(
                conversationId,
                question,
                event -> {
                  sendEvent(emitter, event);
                  if (isTerminalEvent(event.event())) {
                    terminalEventSent.set(true);
                    emitter.complete();
                  }
                });
            if (!terminalEventSent.get()) {
              emitter.complete();
            }
          } catch (RagClientException exception) {
            sendMlErrorThenComplete(emitter, terminalEventSent, exception.getErrorCode());
          } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
          }
        });
    return emitter;
  }

  /**
   * 대화 컨텍스트와 현재 사용자 ACL 로 RAG 질의 요청을 구성하고 RagClient 로 위임한다.
   *
   * <p>입력은 FE 가 보낸 사용자 질문과 대화 ID 이다. 출력은 이 메소드의 반환값이 아니라 `eventConsumer` 로 전달되는 RAG SSE 이벤트 스트림이다.
   * `/ml/query` 요청에는 `spaceKey`, `accessToken`, `cloudId` 를 넣지 않는다.
   *
   * @param conversationId 질의가 속한 대화 식별자
   * @param question 사용자 질문
   * @param eventConsumer RAG SSE 이벤트 소비자
   * @throws BizException 대화가 없거나 ACL 이 비어 있어 RAG 호출을 만들 수 없을 때
   */
  public void relayRagQuery(
      String conversationId, String question, Consumer<RagSseEvent> eventConsumer) {
    Conversation conversation = chatMessagePersistenceService.loadConversation(conversationId);
    List<RagQueryCommand.HistoryMessage> history =
        chatMessagePersistenceService.recentHistory(conversationId, historyTurns);
    chatMessagePersistenceService.saveUserMessage(conversation, question);

    String userId = currentUserProvider.getUserId();
    List<String> groups = currentUserProvider.getGroups();
    // fail-closed 는 userId 기준만 — groups 빈 배열은 허용한다(Confluence group 미소속 사용자도
    // userId 로 user-level/공개 페이지가 매칭되므로). RagQueryCommand 가 null groups 를 빈 배열로 정규화.
    if (userId == null || userId.isBlank()) {
      publishUnauthorizedAclError(eventConsumer);
      return;
    }

    RagQueryCommand command =
        new RagQueryCommand(question, userId, groups, conversationId, history, true);
    AtomicBoolean terminated = new AtomicBoolean(false);
    ChatStreamState streamState = new ChatStreamState(conversation);
    ragClient.streamQuery(
        command, event -> relayCanonicalEvent(event, eventConsumer, terminated, streamState));
  }

  private void publishUnauthorizedAclError(Consumer<RagSseEvent> eventConsumer) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("errorCode", ErrorCode.UNAUTHORIZED.getCode());
    payload.put("message", "RAG 호출에 필요한 ACL 정보가 없습니다.");
    eventConsumer.accept(new RagSseEvent("error", payload));
  }

  private void relayCanonicalEvent(
      RagSseEvent event,
      Consumer<RagSseEvent> eventConsumer,
      AtomicBoolean terminated,
      ChatStreamState streamState) {
    if (terminated.get() || !RELAYABLE_EVENTS.contains(event.event())) {
      return;
    }

    streamState.capture(event);
    RagSseEvent relayedEvent = new RagSseEvent(event.event(), normalizePayload(event, streamState));
    eventConsumer.accept(relayedEvent);
    if (isTerminalEvent(event.event())) {
      terminated.set(true);
    }
  }

  private JsonNode normalizePayload(RagSseEvent event, ChatStreamState streamState) {
    if ("sources".equals(event.event())) {
      return normalizeSourcesPayload(event.data());
    }
    if (DONE_EVENT.equals(event.event())) {
      return normalizeDonePayload(event.data(), streamState.saveAssistantMessage());
    }
    if (ERROR_EVENT.equals(event.event())) {
      return normalizeErrorPayload(event.data());
    }
    return event.data();
  }

  private JsonNode normalizeSourcesPayload(JsonNode payload) {
    if (!payload.isObject()) {
      return payload;
    }
    ObjectNode normalized = payload.deepCopy();
    JsonNode sources = normalized.get("sources");
    if (sources instanceof ArrayNode sourceArray) {
      sourceArray.forEach(this::normalizeSourceUpdatedAt);
    }
    return normalized;
  }

  private void normalizeSourceUpdatedAt(JsonNode source) {
    if (!source.isObject()) {
      return;
    }
    JsonNode sourceUpdatedAt = source.get("sourceUpdatedAt");
    if (sourceUpdatedAt == null || !sourceUpdatedAt.isTextual()) {
      return;
    }
    String kstTimestamp = toKstTimestamp(sourceUpdatedAt.asText());
    if (kstTimestamp != null) {
      ((ObjectNode) source).put("sourceUpdatedAt", kstTimestamp);
    }
  }

  private JsonNode normalizeDonePayload(JsonNode payload, Message assistantMessage) {
    ObjectNode normalized =
        payload.isObject() ? payload.deepCopy() : JsonNodeFactory.instance.objectNode();
    normalized.put("messageId", assistantMessage.getMessageId());
    JsonNode timestamp = normalized.get("timestamp");
    String kstTimestamp =
        timestamp != null && timestamp.isTextual()
            ? toKstTimestamp(timestamp.asText())
            : toKstTimestamp(assistantMessage.getCreatedAt());
    if (kstTimestamp != null) {
      normalized.put("timestamp", kstTimestamp);
    }
    return normalized;
  }

  private JsonNode normalizeErrorPayload(JsonNode payload) {
    ObjectNode normalized =
        payload.isObject() ? payload.deepCopy() : JsonNodeFactory.instance.objectNode();
    String errorCode =
        normalized.hasNonNull("errorCode") ? normalized.get("errorCode").asText() : null;
    if (!RELAYABLE_ERROR_CODES.contains(errorCode)) {
      normalized.put("errorCode", RagClient.ML_SERVER_ERROR);
    }
    if (!normalized.hasNonNull("message") || normalized.get("message").asText().isBlank()) {
      normalized.put("message", "답변 생성 중 오류가 발생했습니다.");
    }
    return normalized;
  }

  private String toKstTimestamp(String timestamp) {
    try {
      return toKstTimestamp(OffsetDateTime.parse(timestamp).toInstant());
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private String toKstTimestamp(Instant instant) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(KST));
  }

  private boolean isTerminalEvent(String eventName) {
    return DONE_EVENT.equals(eventName) || ERROR_EVENT.equals(eventName);
  }

  private void sendEvent(SseEmitter emitter, RagSseEvent event) {
    try {
      emitter.send(SseEmitter.event().name(event.event()).data(event.data()));
    } catch (IOException exception) {
      throw new IllegalStateException("SSE 이벤트 전송에 실패했습니다.", exception);
    }
  }

  private void sendMlErrorThenComplete(
      SseEmitter emitter, AtomicBoolean terminalEventSent, String errorCode) {
    if (!terminalEventSent.compareAndSet(false, true)) {
      return;
    }
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put(
        "errorCode",
        RELAYABLE_ERROR_CODES.contains(errorCode) ? errorCode : RagClient.ML_SERVER_ERROR);
    payload.put("message", messageForMlError(payload.get("errorCode").asText()));
    try {
      emitter.send(SseEmitter.event().name(ERROR_EVENT).data(payload));
      emitter.complete();
    } catch (IOException exception) {
      emitter.completeWithError(exception);
    }
  }

  private String messageForMlError(String errorCode) {
    return switch (errorCode) {
      case RagClient.ML_TIMEOUT -> "ML 응답 시간이 초과되었습니다.";
      case RagClient.ML_CONNECTION_ERROR -> "ML 서버 연결이 중단되었습니다.";
      default -> "답변 생성 중 오류가 발생했습니다.";
    };
  }

  private class ChatStreamState {

    private final Conversation conversation;
    private final StringBuilder answerContent = new StringBuilder();
    private List<MessageSource> sources = List.of();
    private Double confidenceScore;
    private VerificationResult verificationResult;
    private String generatedTitle;
    private Message assistantMessage;

    ChatStreamState(Conversation conversation) {
      this.conversation = conversation;
    }

    void capture(RagSseEvent event) {
      if ("token".equals(event.event())) {
        JsonNode content = event.data().get("content");
        if (content != null && content.isTextual()) {
          answerContent.append(content.asText());
        }
        return;
      }
      if ("sources".equals(event.event())) {
        sources = toMessageSources(event.data());
        return;
      }
      if ("verification".equals(event.event())) {
        JsonNode confidence = event.data().get("confidenceScore");
        if (confidence != null && confidence.isNumber()) {
          confidenceScore = confidence.asDouble();
        }
        JsonNode result = event.data().get("verificationResult");
        if (result != null && result.isTextual()) {
          verificationResult = toVerificationResult(result.asText());
        }
        return;
      }
      if ("meta".equals(event.event())) {
        JsonNode title = event.data().get("title");
        if (title != null && title.isTextual()) {
          generatedTitle = title.asText();
        }
      }
    }

    Message saveAssistantMessage() {
      if (assistantMessage != null) {
        return assistantMessage;
      }
      Message message =
          chatMessagePersistenceService.saveAssistantMessage(
              conversation,
              answerContent.toString(),
              sources,
              confidenceScore,
              verificationResult,
              generatedTitle);
      assistantMessage = message;
      return assistantMessage;
    }
  }

  private List<MessageSource> toMessageSources(JsonNode payload) {
    JsonNode sourceNodes = payload.get("sources");
    if (!(sourceNodes instanceof ArrayNode sourceArray)) {
      return List.of();
    }

    List<MessageSource> parsedSources = new ArrayList<>();
    sourceArray.forEach(
        source -> {
          if (source.isObject()) {
            parsedSources.add(
                MessageSource.builder()
                    .title(textValue(source, "title"))
                    .pageId(textValue(source, "pageId"))
                    .spaceId(textValue(source, "spaceId"))
                    .spaceName(textValue(source, "spaceName"))
                    .url(textValue(source, "url"))
                    .sourceUpdatedAt(instantValue(source, "sourceUpdatedAt"))
                    .relevanceScore(doubleValue(source, "relevanceScore"))
                    .build());
          }
        });
    return parsedSources;
  }

  private VerificationResult toVerificationResult(String value) {
    try {
      return VerificationResult.valueOf(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private Instant instantValue(JsonNode node, String fieldName) {
    String value = textValue(node, fieldName);
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private Double doubleValue(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value != null && value.isNumber() ? value.asDouble() : null;
  }
}

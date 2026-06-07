package com.lina.bff.chat.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.rag.client.RagClient;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final CurrentUserProvider currentUserProvider;
  private final RagClient ragClient;
  private final int historyTurns;

  public ChatService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      CurrentUserProvider currentUserProvider,
      RagClient ragClient,
      @Value("${lina.rag.history-turns:10}") int historyTurns) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.currentUserProvider = currentUserProvider;
    this.ragClient = ragClient;
    this.historyTurns = historyTurns;
  }

  /**
   * FE 로 반환할 SSE 연결을 생성하고 RAG 질의 중계를 시작한다.
   *
   * @param conversationId 질의가 속한 대화 식별자
   * @param question 사용자 질문
   * @return SseEmitter — 공통 Wrapper 를 적용하지 않는 SSE 응답 객체
   */
  public SseEmitter streamChat(String conversationId, String question) {
    SseEmitter emitter = new SseEmitter();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                relayRagQuery(conversationId, question, event -> sendEvent(emitter, event));
                emitter.complete();
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
  @Transactional(readOnly = true)
  public void relayRagQuery(
      String conversationId, String question, Consumer<RagSseEvent> eventConsumer) {
    ensureConversationExists(conversationId);

    String userId = currentUserProvider.getUserId();
    List<String> groups = currentUserProvider.getGroups();
    if (userId == null || userId.isBlank() || groups == null || groups.isEmpty()) {
      publishUnauthorizedAclError(eventConsumer);
      return;
    }

    RagQueryCommand command =
        new RagQueryCommand(
            question, userId, groups, conversationId, recentHistory(conversationId), true);
    ragClient.streamQuery(command, eventConsumer);
  }

  private void ensureConversationExists(String conversationId) {
    conversationRepository
        .findByConversationIdAndDeletedAtIsNull(conversationId)
        .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));
  }

  private List<RagQueryCommand.HistoryMessage> recentHistory(String conversationId) {
    List<Message> messages =
        messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId);
    int fromIndex = Math.max(messages.size() - historyTurns, 0);
    return messages.subList(fromIndex, messages.size()).stream()
        .map(
            message ->
                new RagQueryCommand.HistoryMessage(message.getRole().name(), message.getContent()))
        .toList();
  }

  private void publishUnauthorizedAclError(Consumer<RagSseEvent> eventConsumer) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("errorCode", ErrorCode.UNAUTHORIZED.getCode());
    payload.put("message", "RAG 호출에 필요한 ACL 정보가 없습니다.");
    eventConsumer.accept(new RagSseEvent("error", payload));
  }

  private void sendEvent(SseEmitter emitter, RagSseEvent event) {
    try {
      emitter.send(SseEmitter.event().name(event.event()).data(event.data()));
    } catch (IOException exception) {
      throw new IllegalStateException("SSE 이벤트 전송에 실패했습니다.", exception);
    }
  }
}

package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.entity.VerificationResult;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.rag.client.RagClient;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ChatMessagePersistenceService chatMessagePersistenceService;
  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private RagClient ragClient;
  @Mock private Consumer<RagSseEvent> eventConsumer;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    chatService =
        new ChatService(
            chatMessagePersistenceService,
            currentUserProvider,
            ragClient,
            new SyncTaskExecutor(),
            2);
  }

  @Test
  @DisplayName("RAG 호출 시 질문, 대화 ID, 최근 history, ACL, stream=true 를 전달한다")
  void shouldRelayQueryCommandToRagClient() {
    Conversation conversation =
        Conversation.builder().conversationId("conv-1").userId("user-001").title("S3 대화").build();
    List<RagQueryCommand.HistoryMessage> history =
        List.of(
            new RagQueryCommand.HistoryMessage("user", "S3 장애 이력 알려줘"),
            new RagQueryCommand.HistoryMessage("assistant", "최근 S3 장애는 3건입니다."));
    when(chatMessagePersistenceService.loadConversation("conv-1")).thenReturn(conversation);
    when(chatMessagePersistenceService.recentHistory("conv-1", 2)).thenReturn(history);
    when(chatMessagePersistenceService.saveUserMessage(conversation, "지난번 S3 권한 오류는 어떻게 해결했어?"))
        .thenReturn(Message.builder().conversationId("conv-1").role(MessageRole.user).build());
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of("Cloud-Control-Center"));

    chatService.relayRagQuery("conv-1", "지난번 S3 권한 오류는 어떻게 해결했어?", eventConsumer);

    ArgumentCaptor<RagQueryCommand> commandCaptor = ArgumentCaptor.forClass(RagQueryCommand.class);
    verify(ragClient).streamQuery(commandCaptor.capture(), any());
    RagQueryCommand command = commandCaptor.getValue();
    assertThat(command.question()).isEqualTo("지난번 S3 권한 오류는 어떻게 해결했어?");
    assertThat(command.conversationId()).isEqualTo("conv-1");
    assertThat(command.userId()).isEqualTo("user-001");
    assertThat(command.groups()).containsExactly("Cloud-Control-Center");
    assertThat(command.stream()).isTrue();
    assertThat(command.history()).containsExactlyElementsOf(history);

    assertThat(objectMapper.valueToTree(command).has("spaceKey")).isFalse();
    assertThat(objectMapper.valueToTree(command).has("accessToken")).isFalse();
    assertThat(objectMapper.valueToTree(command).has("cloudId")).isFalse();
  }

  @Test
  @DisplayName("현재 사용자 ACL 이 비어 있으면 RAG 호출 없이 UNAUTHORIZED SSE error 이벤트로 종료한다")
  void shouldRejectRagCallWhenAclMissing() {
    Conversation conversation = Conversation.builder().conversationId("conv-1").build();
    when(chatMessagePersistenceService.loadConversation("conv-1")).thenReturn(conversation);
    when(chatMessagePersistenceService.recentHistory("conv-1", 2)).thenReturn(List.of());
    when(chatMessagePersistenceService.saveUserMessage(conversation, "질문"))
        .thenReturn(Message.builder().conversationId("conv-1").role(MessageRole.user).build());
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of());

    chatService.relayRagQuery("conv-1", "질문", eventConsumer);

    verify(ragClient, never()).streamQuery(any(), any());
    ArgumentCaptor<RagSseEvent> eventCaptor = ArgumentCaptor.forClass(RagSseEvent.class);
    verify(eventConsumer).accept(eventCaptor.capture());
    RagSseEvent event = eventCaptor.getValue();
    assertThat(event.event()).isEqualTo("error");
    assertThat(event.data().get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
    assertThat(event.data().get("message").asText()).isEqualTo("RAG 호출에 필요한 ACL 정보가 없습니다.");
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("정본 SSE 이벤트만 중계하고 done 에 저장된 assistant messageId 를 채운다")
  void shouldRelayCanonicalEventsAndFillDoneMessageIdFromSavedAssistant() {
    Conversation conversation = Conversation.builder().conversationId("conv-1").build();
    when(chatMessagePersistenceService.loadConversation("conv-1")).thenReturn(conversation);
    when(chatMessagePersistenceService.recentHistory("conv-1", 2)).thenReturn(List.of());
    when(chatMessagePersistenceService.saveUserMessage(conversation, "질문"))
        .thenReturn(Message.builder().conversationId("conv-1").role(MessageRole.user).build());
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of("Cloud-Control-Center"));
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<RagSseEvent> consumer = invocation.getArgument(1, Consumer.class);
              consumer.accept(new RagSseEvent("debug", JsonNodeFactory.instance.objectNode()));

              ObjectNode tokenPayload = JsonNodeFactory.instance.objectNode();
              tokenPayload.put("content", "S3 권한 오류는");
              consumer.accept(new RagSseEvent("token", tokenPayload));

              ObjectNode sourcesPayload = JsonNodeFactory.instance.objectNode();
              ObjectNode source = sourcesPayload.putArray("sources").addObject();
              source.put("title", "S3 트러블슈팅 가이드");
              source.put("pageId", "12345");
              source.put("spaceId", "98310");
              source.put("spaceName", "Cloud Control Center");
              source.put("url", "https://confluence.example.com/pages/12345");
              source.put("sourceUpdatedAt", "2026-04-15T09:30:00Z");
              source.put("relevanceScore", 0.92);
              consumer.accept(new RagSseEvent("sources", sourcesPayload));

              ObjectNode verificationPayload = JsonNodeFactory.instance.objectNode();
              verificationPayload.put("confidenceScore", 0.85);
              verificationPayload.put("verificationResult", "SUPPORTED");
              consumer.accept(new RagSseEvent("verification", verificationPayload));

              ObjectNode donePayload = JsonNodeFactory.instance.objectNode();
              donePayload.put("timestamp", "2026-06-07T01:02:03Z");
              consumer.accept(new RagSseEvent("done", donePayload));
              consumer.accept(new RagSseEvent("token", JsonNodeFactory.instance.objectNode()));
              return null;
            })
        .when(ragClient)
        .streamQuery(any(), any());
    when(chatMessagePersistenceService.saveAssistantMessage(
            any(Conversation.class), any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                Message.builder()
                    .messageId("assistant-msg-1")
                    .conversationId("conv-1")
                    .role(MessageRole.assistant)
                    .content(invocation.getArgument(1))
                    .sources(invocation.getArgument(2))
                    .confidenceScore(invocation.getArgument(3))
                    .verificationResult(invocation.getArgument(4))
                    .createdAt(Instant.parse("2026-06-07T01:02:03Z"))
                    .build());

    chatService.relayRagQuery("conv-1", "질문", eventConsumer);

    ArgumentCaptor<RagSseEvent> eventCaptor = ArgumentCaptor.forClass(RagSseEvent.class);
    verify(eventConsumer, times(4)).accept(eventCaptor.capture());
    List<RagSseEvent> events = eventCaptor.getAllValues();
    assertThat(events)
        .extracting(RagSseEvent::event)
        .containsExactly("token", "sources", "verification", "done");
    assertThat(events.get(1).data().get("sources").get(0).get("sourceUpdatedAt").asText())
        .isEqualTo("2026-04-15T18:30:00+09:00");
    assertThat(events.get(3).data().get("timestamp").asText())
        .isEqualTo("2026-06-07T10:02:03+09:00");

    ArgumentCaptor<List<MessageSource>> sourcesCaptor = ArgumentCaptor.forClass(List.class);
    verify(chatMessagePersistenceService)
        .saveAssistantMessage(
            any(Conversation.class),
            org.mockito.ArgumentMatchers.eq("S3 권한 오류는"),
            sourcesCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(0.85),
            org.mockito.ArgumentMatchers.eq(VerificationResult.SUPPORTED));
    assertThat(sourcesCaptor.getValue())
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.getTitle()).isEqualTo("S3 트러블슈팅 가이드");
              assertThat(source.getSourceUpdatedAt())
                  .isEqualTo(Instant.parse("2026-04-15T09:30:00Z"));
              assertThat(source.getRelevanceScore()).isEqualTo(0.92);
            });
    assertThat(events.get(3).data().get("messageId").asText()).isEqualTo("assistant-msg-1");
  }

  @Test
  @DisplayName("RAG error 이벤트는 유효한 errorCode 를 그대로 중계하고 assistant 메시지는 저장하지 않는다")
  void shouldPassthroughValidRagErrorWithoutSavingAssistantMessage() {
    Conversation conversation = Conversation.builder().conversationId("conv-1").build();
    when(chatMessagePersistenceService.loadConversation("conv-1")).thenReturn(conversation);
    when(chatMessagePersistenceService.recentHistory("conv-1", 2)).thenReturn(List.of());
    when(chatMessagePersistenceService.saveUserMessage(conversation, "질문"))
        .thenReturn(Message.builder().conversationId("conv-1").role(MessageRole.user).build());
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of("Cloud-Control-Center"));
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<RagSseEvent> consumer = invocation.getArgument(1, Consumer.class);
              ObjectNode errorPayload = JsonNodeFactory.instance.objectNode();
              errorPayload.put("errorCode", "ML_TIMEOUT");
              errorPayload.put("message", "ML 응답이 지연되었습니다.");
              consumer.accept(new RagSseEvent("error", errorPayload));
              return null;
            })
        .when(ragClient)
        .streamQuery(any(), any());

    chatService.relayRagQuery("conv-1", "질문", eventConsumer);

    ArgumentCaptor<RagSseEvent> eventCaptor = ArgumentCaptor.forClass(RagSseEvent.class);
    verify(eventConsumer).accept(eventCaptor.capture());
    RagSseEvent event = eventCaptor.getValue();
    assertThat(event.event()).isEqualTo("error");
    assertThat(event.data().get("errorCode").asText()).isEqualTo("ML_TIMEOUT");
    assertThat(event.data().get("message").asText()).isEqualTo("ML 응답이 지연되었습니다.");
    verify(chatMessagePersistenceService, never())
        .saveAssistantMessage(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("streamChat 은 RAG 중계 작업을 Spring TaskExecutor 에 제출하고 SseEmitter 를 즉시 반환한다")
  void shouldSubmitStreamingWorkToTaskExecutor() {
    RecordingTaskExecutor taskExecutor = new RecordingTaskExecutor();
    ChatService chatService =
        new ChatService(
            chatMessagePersistenceService, currentUserProvider, ragClient, taskExecutor, 2);

    SseEmitter emitter = chatService.streamChat("conv-1", "질문");

    assertThat(emitter).isNotNull();
    assertThat(taskExecutor.hasSubmittedTask()).isTrue();
    verify(chatMessagePersistenceService, never()).loadConversation(any());
  }

  private static class RecordingTaskExecutor implements TaskExecutor {

    private Runnable submittedTask;

    @Override
    public void execute(Runnable task) {
      this.submittedTask = task;
    }

    boolean hasSubmittedTask() {
      return submittedTask != null;
    }
  }
}

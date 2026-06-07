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
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.rag.client.RagClient;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private RagClient ragClient;
  @Mock private Consumer<RagSseEvent> eventConsumer;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    chatService =
        new ChatService(
            conversationRepository,
            messageRepository,
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
    Message oldest =
        Message.builder()
            .messageId("msg-1")
            .conversationId("conv-1")
            .role(MessageRole.user)
            .content("가장 오래된 질문")
            .createdAt(Instant.parse("2026-05-06T10:00:00Z"))
            .build();
    Message recentUser =
        Message.builder()
            .messageId("msg-2")
            .conversationId("conv-1")
            .role(MessageRole.user)
            .content("S3 장애 이력 알려줘")
            .createdAt(Instant.parse("2026-05-06T10:01:00Z"))
            .build();
    Message recentAssistant =
        Message.builder()
            .messageId("msg-3")
            .conversationId("conv-1")
            .role(MessageRole.assistant)
            .content("최근 S3 장애는 3건입니다.")
            .createdAt(Instant.parse("2026-05-06T10:02:00Z"))
            .build();
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1"))
        .thenReturn(List.of(oldest, recentUser, recentAssistant));
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
    assertThat(command.history())
        .containsExactly(
            new RagQueryCommand.HistoryMessage("user", "S3 장애 이력 알려줘"),
            new RagQueryCommand.HistoryMessage("assistant", "최근 S3 장애는 3건입니다."));

    assertThat(objectMapper.valueToTree(command).has("spaceKey")).isFalse();
    assertThat(objectMapper.valueToTree(command).has("accessToken")).isFalse();
    assertThat(objectMapper.valueToTree(command).has("cloudId")).isFalse();
  }

  @Test
  @DisplayName("현재 사용자 ACL 이 비어 있으면 RAG 호출 없이 UNAUTHORIZED SSE error 이벤트로 종료한다")
  void shouldRejectRagCallWhenAclMissing() {
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(Conversation.builder().conversationId("conv-1").build()));
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
  @DisplayName("정본 SSE 이벤트만 중계하고 sourceUpdatedAt 과 done timestamp 를 KST 로 직렬화한다")
  void shouldRelayCanonicalEventsAndSerializeTimestampsAsKst() {
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(Conversation.builder().conversationId("conv-1").build()));
    when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1"))
        .thenReturn(List.of());
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of("Cloud-Control-Center"));
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<RagSseEvent> consumer = invocation.getArgument(1, Consumer.class);
              consumer.accept(new RagSseEvent("debug", JsonNodeFactory.instance.objectNode()));

              ObjectNode sourcesPayload = JsonNodeFactory.instance.objectNode();
              ObjectNode source = sourcesPayload.putArray("sources").addObject();
              source.put("title", "S3 트러블슈팅 가이드");
              source.put("sourceUpdatedAt", "2026-04-15T09:30:00Z");
              consumer.accept(new RagSseEvent("sources", sourcesPayload));

              ObjectNode donePayload = JsonNodeFactory.instance.objectNode();
              donePayload.put("messageId", "msg-uuid-001");
              donePayload.put("timestamp", "2026-06-07T01:02:03Z");
              consumer.accept(new RagSseEvent("done", donePayload));
              consumer.accept(new RagSseEvent("token", JsonNodeFactory.instance.objectNode()));
              return null;
            })
        .when(ragClient)
        .streamQuery(any(), any());

    chatService.relayRagQuery("conv-1", "질문", eventConsumer);

    ArgumentCaptor<RagSseEvent> eventCaptor = ArgumentCaptor.forClass(RagSseEvent.class);
    verify(eventConsumer, times(2)).accept(eventCaptor.capture());
    List<RagSseEvent> events = eventCaptor.getAllValues();
    assertThat(events).extracting(RagSseEvent::event).containsExactly("sources", "done");
    assertThat(events.get(0).data().get("sources").get(0).get("sourceUpdatedAt").asText())
        .isEqualTo("2026-04-15T18:30:00+09:00");
    assertThat(events.get(1).data().get("messageId").asText()).isEqualTo("msg-uuid-001");
    assertThat(events.get(1).data().get("timestamp").asText())
        .isEqualTo("2026-06-07T10:02:03+09:00");
  }

  @Test
  @DisplayName("streamChat 은 RAG 중계 작업을 Spring TaskExecutor 에 제출하고 SseEmitter 를 즉시 반환한다")
  void shouldSubmitStreamingWorkToTaskExecutor() {
    RecordingTaskExecutor taskExecutor = new RecordingTaskExecutor();
    ChatService chatService =
        new ChatService(
            conversationRepository,
            messageRepository,
            currentUserProvider,
            ragClient,
            taskExecutor,
            2);

    SseEmitter emitter = chatService.streamChat("conv-1", "질문");

    assertThat(emitter).isNotNull();
    assertThat(taskExecutor.hasSubmittedTask()).isTrue();
    verify(conversationRepository, never()).findByConversationIdAndDeletedAtIsNull(any());
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

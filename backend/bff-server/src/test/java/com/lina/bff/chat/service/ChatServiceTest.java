package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.rag.client.RagClient;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.bff.rag.client.dto.RagSseEvent;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private RagClient ragClient;
  @Mock private Consumer<RagSseEvent> eventConsumer;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("RAG 호출 시 질문, 대화 ID, 최근 history, ACL, stream=true 를 전달한다")
  void shouldRelayQueryCommandToRagClient() {
    ChatService chatService =
        new ChatService(
            conversationRepository, messageRepository, currentUserProvider, ragClient, 2);
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
  @DisplayName("현재 사용자 ACL 이 비어 있으면 RAG 호출을 만들지 않는다")
  void shouldRejectRagCallWhenAclMissing() {
    ChatService chatService =
        new ChatService(
            conversationRepository, messageRepository, currentUserProvider, ragClient, 2);
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(Conversation.builder().conversationId("conv-1").build()));
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(currentUserProvider.getGroups()).thenReturn(List.of());

    assertThatThrownBy(() -> chatService.relayRagQuery("conv-1", "질문", eventConsumer))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    verify(ragClient, never()).streamQuery(any(), any());
  }
}

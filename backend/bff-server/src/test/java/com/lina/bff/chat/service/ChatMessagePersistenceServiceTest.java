package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.entity.VerificationResult;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessagePersistenceServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;

  @Test
  @DisplayName("활성 대화를 조회하고 없으면 RESOURCE_NOT_FOUND 예외를 던진다")
  void shouldLoadActiveConversationOrThrowNotFound() {
    ChatMessagePersistenceService service =
        new ChatMessagePersistenceService(conversationRepository, messageRepository);
    Conversation conversation = Conversation.builder().conversationId("conv-1").build();
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("missing"))
        .thenReturn(Optional.empty());

    assertThat(service.loadConversation("conv-1")).isEqualTo(conversation);
    assertThatThrownBy(() -> service.loadConversation("missing"))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("최근 historyTurns 개 메시지를 RAG history DTO 로 변환한다")
  void shouldBuildRecentHistoryMessages() {
    ChatMessagePersistenceService service =
        new ChatMessagePersistenceService(conversationRepository, messageRepository);
    Message oldest =
        Message.builder().conversationId("conv-1").role(MessageRole.user).content("오래된 질문").build();
    Message recentUser =
        Message.builder().conversationId("conv-1").role(MessageRole.user).content("최근 질문").build();
    Message recentAssistant =
        Message.builder()
            .conversationId("conv-1")
            .role(MessageRole.assistant)
            .content("최근 답변")
            .build();
    when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1"))
        .thenReturn(List.of(oldest, recentUser, recentAssistant));

    List<RagQueryCommand.HistoryMessage> history = service.recentHistory("conv-1", 2);

    assertThat(history)
        .containsExactly(
            new RagQueryCommand.HistoryMessage("user", "최근 질문"),
            new RagQueryCommand.HistoryMessage("assistant", "최근 답변"));
  }

  @Test
  @DisplayName("user 메시지를 저장하고 대화 lastMessageAt 을 갱신한다")
  void shouldSaveUserMessageAndTouchConversation() {
    ChatMessagePersistenceService service =
        new ChatMessagePersistenceService(conversationRepository, messageRepository);
    Conversation conversation =
        Conversation.builder()
            .conversationId("conv-1")
            .lastMessageAt(Instant.parse("2026-06-07T00:00:00Z"))
            .build();
    Instant createdAt = Instant.parse("2026-06-07T01:00:00Z");
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(
            invocation -> {
              Message message = invocation.getArgument(0);
              return Message.builder()
                  .messageId(message.getMessageId())
                  .conversationId(message.getConversationId())
                  .role(message.getRole())
                  .content(message.getContent())
                  .createdAt(createdAt)
                  .build();
            });

    Message saved = service.saveUserMessage(conversation, "질문");

    assertThat(saved.getRole()).isEqualTo(MessageRole.user);
    assertThat(saved.getContent()).isEqualTo("질문");
    assertThat(conversation.getLastMessageAt()).isEqualTo(createdAt);
    verify(conversationRepository).save(conversation);
  }

  @Test
  @DisplayName("assistant 메시지를 출처·검증 결과와 함께 저장하고 대화 lastMessageAt 을 갱신한다")
  void shouldSaveAssistantMessageAndTouchConversation() {
    ChatMessagePersistenceService service =
        new ChatMessagePersistenceService(conversationRepository, messageRepository);
    Conversation conversation =
        Conversation.builder()
            .conversationId("conv-1")
            .lastMessageAt(Instant.parse("2026-06-07T00:00:00Z"))
            .build();
    MessageSource source =
        MessageSource.builder().title("S3 트러블슈팅 가이드").relevanceScore(0.92).build();
    Instant createdAt = Instant.parse("2026-06-07T01:00:00Z");
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(
            invocation -> {
              Message message = invocation.getArgument(0);
              return Message.builder()
                  .messageId(message.getMessageId())
                  .conversationId(message.getConversationId())
                  .role(message.getRole())
                  .content(message.getContent())
                  .sources(message.getSources())
                  .confidenceScore(message.getConfidenceScore())
                  .verificationResult(message.getVerificationResult())
                  .createdAt(createdAt)
                  .build();
            });

    Message saved =
        service.saveAssistantMessage(
            conversation, "답변", List.of(source), 0.85, VerificationResult.SUPPORTED);

    assertThat(saved.getRole()).isEqualTo(MessageRole.assistant);
    assertThat(saved.getContent()).isEqualTo("답변");
    assertThat(saved.getSources()).containsExactly(source);
    assertThat(saved.getConfidenceScore()).isEqualTo(0.85);
    assertThat(saved.getVerificationResult()).isEqualTo(VerificationResult.SUPPORTED);
    assertThat(conversation.getLastMessageAt()).isEqualTo(createdAt);
    verify(conversationRepository).save(conversation);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(messageRepository).save(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getSources()).containsExactly(source);
  }

  @Test
  @DisplayName("대화 조회 실패 시 메시지를 저장하지 않는다")
  void shouldNotSaveMessageWhenConversationMissing() {
    ChatMessagePersistenceService service =
        new ChatMessagePersistenceService(conversationRepository, messageRepository);
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadConversation("missing")).isInstanceOf(BizException.class);

    verify(messageRepository, never()).save(any());
  }
}

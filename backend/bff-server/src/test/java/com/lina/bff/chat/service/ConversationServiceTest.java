package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.chat.dto.ConversationListResponse;
import com.lina.bff.chat.dto.ConversationSummaryResponse;
import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.dto.MessageHistoryResponse;
import com.lina.bff.chat.dto.MessageResponse;
import com.lina.bff.chat.dto.SourceResponse;
import com.lina.bff.chat.dto.UpdateConversationRequest;
import com.lina.bff.chat.dto.UpdateConversationResponse;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.entity.VerificationResult;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private ConversationService conversationService;

  @Test
  @DisplayName("대화 CRUD 응답 DTO timestamp 필드는 ZonedDateTime 으로 노출한다")
  void shouldExposeConversationTimestampsAsZonedDateTimeFields() {
    assertThat(recordComponentType(CreateConversationResponse.class, "createdAt"))
        .isEqualTo(ZonedDateTime.class);
    assertThat(recordComponentType(ConversationSummaryResponse.class, "lastMessageAt"))
        .isEqualTo(ZonedDateTime.class);
    assertThat(recordComponentType(UpdateConversationResponse.class, "updatedAt"))
        .isEqualTo(ZonedDateTime.class);
  }

  @Test
  @DisplayName("메시지 이력 응답 DTO timestamp 필드는 ZonedDateTime 으로 노출한다")
  void shouldExposeMessageHistoryTimestampsAsZonedDateTimeFields() {
    assertThat(recordComponentType(MessageResponse.class, "createdAt"))
        .isEqualTo(ZonedDateTime.class);
    assertThat(recordComponentType(SourceResponse.class, "sourceUpdatedAt"))
        .isEqualTo(ZonedDateTime.class);
  }

  @Test
  @DisplayName("새 대화는 현재 사용자 기준 기본 제목과 isPinned=false 로 생성한다")
  void shouldCreateConversationForCurrentUser() {
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = conversationService.createConversation();

    assertThat(response.title()).isEqualTo("새 대화");
    assertThat(response.isPinned()).isFalse();
    assertThat(response.createdAt().getZone().getId()).isEqualTo("Asia/Seoul");

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo("user-001");
    assertThat(captor.getValue().isPinned()).isFalse();
  }

  @Test
  @DisplayName("현재 사용자 식별자가 비어 있으면 새 대화를 생성하지 않는다")
  void shouldRejectBlankCurrentUserId() {
    when(currentUserProvider.getUserId()).thenReturn(" ");

    assertThatThrownBy(() -> conversationService.createConversation())
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);

    verify(conversationRepository, never()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("대화 목록은 현재 사용자 기준 고정 우선, 최신순으로 조회하고 KST timestamp 를 반환한다")
  void shouldListConversationsForCurrentUser() {
    Instant base = Instant.parse("2026-05-06T10:00:00Z");
    Conversation pinned =
        Conversation.builder()
            .conversationId("conv-pinned")
            .userId("user-001")
            .title("고정 대화")
            .isPinned(true)
            .lastMessageAt(base.minus(1, ChronoUnit.HOURS))
            .build();
    Conversation recent =
        Conversation.builder()
            .conversationId("conv-recent")
            .userId("user-001")
            .title("최신 대화")
            .isPinned(false)
            .lastMessageAt(base)
            .build();
    PageRequest pageRequest = PageRequest.of(0, 20);
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(
            "user-001", pageRequest))
        .thenReturn(new PageImpl<>(List.of(pinned, recent), pageRequest, 2));

    ConversationListResponse response = conversationService.listConversations(0, 20);

    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.conversations())
        .extracting("conversationId")
        .containsExactly("conv-pinned", "conv-recent");
    ZonedDateTime firstLastMessageAt = response.conversations().getFirst().lastMessageAt();
    assertThat(firstLastMessageAt.getZone().getId()).isEqualTo("Asia/Seoul");
    assertThat(firstLastMessageAt.toInstant()).isEqualTo(base.minus(1, ChronoUnit.HOURS));
  }

  @Test
  @DisplayName("현재 사용자 식별자가 비어 있으면 대화 목록을 조회하지 않는다")
  void shouldRejectListConversationsWhenCurrentUserIdBlank() {
    when(currentUserProvider.getUserId()).thenReturn("");

    assertThatThrownBy(() -> conversationService.listConversations(0, 20))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);

    verify(conversationRepository, never())
        .findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(any(), any());
  }

  @Test
  @DisplayName("대화 제목과 고정 여부를 부분 수정하고 updatedAt 을 KST 로 반환한다")
  void shouldUpdateConversationPartially() {
    Conversation conversation =
        Conversation.builder()
            .conversationId("conv-1")
            .userId("user-001")
            .title("새 대화")
            .isPinned(false)
            .updatedAt(Instant.parse("2026-05-06T10:00:00Z"))
            .build();
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        conversationService.updateConversation(
            "conv-1", new UpdateConversationRequest("수정된 대화 제목", true));

    assertThat(response.conversationId()).isEqualTo("conv-1");
    assertThat(response.title()).isEqualTo("수정된 대화 제목");
    assertThat(response.isPinned()).isTrue();
    assertThat(response.updatedAt().getZone().getId()).isEqualTo("Asia/Seoul");

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("수정된 대화 제목");
    assertThat(captor.getValue().isPinned()).isTrue();
  }

  @Test
  @DisplayName("대화 수정 요청에 title 과 isPinned 이 모두 없으면 INVALID_REQUEST")
  void shouldRejectEmptyUpdateConversationRequest() {
    assertThatThrownBy(
            () ->
                conversationService.updateConversation(
                    "conv-1", new UpdateConversationRequest(null, null)))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);

    verify(conversationRepository, never()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("존재하지 않거나 삭제된 대화는 수정할 수 없다")
  void shouldRejectUpdateMissingOrDeletedConversation() {
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                conversationService.updateConversation(
                    "conv-missing", new UpdateConversationRequest("수정된 대화 제목", null)))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(conversationRepository, never()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("대화를 soft delete 하고 deletedAt 이 채워진 문서를 저장한다")
  void shouldSoftDeleteConversation() {
    Conversation conversation =
        Conversation.builder().conversationId("conv-1").userId("user-001").title("삭제할 대화").build();
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    conversationService.deleteConversation("conv-1");

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("존재하지 않거나 삭제된 대화는 삭제할 수 없다")
  void shouldRejectDeleteMissingOrDeletedConversation() {
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.deleteConversation("conv-missing"))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(conversationRepository, never()).save(any(Conversation.class));
  }

  @Test
  @DisplayName("메시지 이력은 대화 존재 확인 후 활성 메시지를 시간순 DTO 로 변환하고 timestamp 를 KST 로 반환한다")
  void shouldGetMessageHistoryWithSources() {
    Conversation conversation =
        Conversation.builder().conversationId("conv-1").userId("user-001").title("S3 대화").build();
    Message userMessage =
        Message.builder()
            .messageId("msg-user")
            .conversationId("conv-1")
            .role(MessageRole.user)
            .content("S3 권한 오류는?")
            .createdAt(Instant.parse("2026-05-06T10:00:00Z"))
            .build();
    Message assistantMessage =
        Message.builder()
            .messageId("msg-assistant")
            .conversationId("conv-1")
            .role(MessageRole.assistant)
            .content("IAM 정책을 수정했습니다.")
            .sources(
                List.of(
                    MessageSource.builder()
                        .title("S3 트러블슈팅 가이드")
                        .pageId("12345")
                        .spaceId("98310")
                        .spaceName("Cloud Control Center")
                        .url("https://confluence.example.com/pages/12345")
                        .sourceUpdatedAt(Instant.parse("2026-04-15T09:30:00Z"))
                        .relevanceScore(0.92)
                        .build()))
            .confidenceScore(0.85)
            .verificationResult(VerificationResult.SUPPORTED)
            .createdAt(Instant.parse("2026-05-06T10:00:05Z"))
            .build();
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-1"))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1"))
        .thenReturn(List.of(userMessage, assistantMessage));

    MessageHistoryResponse response = conversationService.getMessageHistory("conv-1");

    assertThat(response.conversationId()).isEqualTo("conv-1");
    assertThat(response.messages())
        .extracting(MessageResponse::messageId)
        .containsExactly("msg-user", "msg-assistant");
    MessageResponse assistant = response.messages().get(1);
    assertThat(assistant.role()).isEqualTo(MessageRole.assistant);
    assertThat(assistant.confidenceScore()).isEqualTo(0.85);
    assertThat(assistant.verificationResult()).isEqualTo(VerificationResult.SUPPORTED);
    assertThat(assistant.createdAt().getZone().getId()).isEqualTo("Asia/Seoul");
    assertThat(assistant.createdAt().toInstant()).isEqualTo(Instant.parse("2026-05-06T10:00:05Z"));
    assertThat(assistant.sources()).hasSize(1);
    assertThat(assistant.sources().getFirst().sourceUpdatedAt().getZone().getId())
        .isEqualTo("Asia/Seoul");
    assertThat(assistant.sources().getFirst().sourceUpdatedAt().toInstant())
        .isEqualTo(Instant.parse("2026-04-15T09:30:00Z"));
  }

  @Test
  @DisplayName("없는/삭제된 대화의 메시지 이력 조회는 RESOURCE_NOT_FOUND")
  void shouldRejectMessageHistoryWhenConversationMissingOrDeleted() {
    when(conversationRepository.findByConversationIdAndDeletedAtIsNull("conv-missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.getMessageHistory("conv-missing"))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(messageRepository, never())
        .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(any());
  }

  private Class<?> recordComponentType(Class<?> recordType, String componentName) {
    for (RecordComponent component : recordType.getRecordComponents()) {
      if (component.getName().equals(componentName)) {
        return component.getType();
      }
    }
    throw new IllegalArgumentException(
        recordType.getSimpleName() + "." + componentName + " component not found");
  }
}

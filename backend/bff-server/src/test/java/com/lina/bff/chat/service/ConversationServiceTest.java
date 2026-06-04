package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private ConversationService conversationService;

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
}

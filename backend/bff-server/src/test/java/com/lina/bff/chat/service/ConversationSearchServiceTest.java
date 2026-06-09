package com.lina.bff.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lina.bff.chat.dto.ConversationSearchResponse;
import com.lina.bff.chat.dto.ConversationSearchResultResponse;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationSearchServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private CurrentUserProvider currentUserProvider;

  @InjectMocks private ConversationSearchService conversationSearchService;

  private Conversation conversation(
      String id, String title, boolean pinned, Instant lastMessageAt) {
    return Conversation.builder()
        .conversationId(id)
        .userId("user-001")
        .title(title)
        .isPinned(pinned)
        .lastMessageAt(lastMessageAt)
        .build();
  }

  private Message message(String id, String conversationId, MessageRole role, String content) {
    return Message.builder()
        .messageId(id)
        .conversationId(conversationId)
        .role(role)
        .content(content)
        .createdAt(Instant.parse("2026-05-06T10:00:00Z"))
        .build();
  }

  @Test
  @DisplayName("q 가 trim 후 2자 미만이면 INVALID_SEARCH_QUERY 이고 저장소를 호출하지 않는다")
  void shouldRejectTooShortQuery() {
    assertThatThrownBy(() -> conversationSearchService.search("  a ", 0, 20))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SEARCH_QUERY);

    verifyNoInteractions(conversationRepository, messageRepository, currentUserProvider);
  }

  @Test
  @DisplayName("q 가 trim 후 50자를 초과하면 INVALID_SEARCH_QUERY")
  void shouldRejectTooLongQuery() {
    String tooLong = "a".repeat(51);

    assertThatThrownBy(() -> conversationSearchService.search(tooLong, 0, 20))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SEARCH_QUERY);

    verifyNoInteractions(conversationRepository, messageRepository, currentUserProvider);
  }

  @Test
  @DisplayName("size 가 50 을 초과하면 INVALID_SEARCH_QUERY")
  void shouldRejectSizeOverMax() {
    assertThatThrownBy(() -> conversationSearchService.search("S3", 0, 51))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_SEARCH_QUERY);

    verifyNoInteractions(conversationRepository, messageRepository, currentUserProvider);
  }

  @Test
  @DisplayName("현재 사용자 식별자가 비어 있으면 INVALID_REQUEST")
  void shouldRejectBlankCurrentUserId() {
    when(currentUserProvider.getUserId()).thenReturn(" ");

    assertThatThrownBy(() -> conversationSearchService.search("S3", 0, 20))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_REQUEST);

    verifyNoInteractions(conversationRepository, messageRepository);
  }

  @Test
  @DisplayName("본인 활성 대화가 없으면 메시지 검색 없이 빈 결과를 반환한다")
  void shouldReturnEmptyWhenNoOwnedConversations() {
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.findByUserIdAndDeletedAtIsNull("user-001")).thenReturn(List.of());

    ConversationSearchResponse response = conversationSearchService.search("S3", 0, 20);

    assertThat(response.results()).isEmpty();
    assertThat(response.totalCount()).isZero();
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
    verify(messageRepository, never())
        .searchActiveByConversationIdsAndContent(anyCollection(), any());
  }

  @Test
  @DisplayName("대화 단위로 묶어 matchCount 와 최대 3개 샘플을 lastMessageAt 최신순으로 KST 로 반환한다")
  void shouldGroupByConversationWithMatchCapAndKstSort() {
    Instant base = Instant.parse("2026-05-06T10:00:00Z");
    Conversation convA = conversation("conv-A", "S3 대화", false, base);
    Conversation convB = conversation("conv-B", "IAM 대화", true, base.plus(1, ChronoUnit.HOURS));
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.findByUserIdAndDeletedAtIsNull("user-001"))
        .thenReturn(List.of(convA, convB));
    when(messageRepository.searchActiveByConversationIdsAndContent(anyCollection(), anyString()))
        .thenReturn(
            List.of(
                message("m1", "conv-A", MessageRole.user, "S3 권한 오류"),
                message("m2", "conv-A", MessageRole.assistant, "S3 정책 수정"),
                message("m3", "conv-A", MessageRole.user, "다시 S3 확인"),
                message("m4", "conv-A", MessageRole.assistant, "S3 해결"),
                message("m5", "conv-B", MessageRole.user, "IAM 과 S3 비교")));

    ConversationSearchResponse response = conversationSearchService.search("S3", 0, 20);

    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.results())
        .extracting(ConversationSearchResultResponse::conversationId)
        .containsExactly("conv-B", "conv-A");

    ConversationSearchResultResponse first = response.results().get(0);
    assertThat(first.isPinned()).isTrue();
    assertThat(first.matchCount()).isEqualTo(1);
    assertThat(first.matchedMessages()).hasSize(1);
    assertThat(first.lastMessageAt().getZone().getId()).isEqualTo("Asia/Seoul");
    assertThat(first.matchedMessages().get(0).role()).isEqualTo(MessageRole.user);
    assertThat(first.matchedMessages().get(0).snippet()).contains("S3");
    assertThat(first.matchedMessages().get(0).matchPositions()).isNotEmpty();
    assertThat(first.matchedMessages().get(0).createdAt().getZone().getId())
        .isEqualTo("Asia/Seoul");

    ConversationSearchResultResponse second = response.results().get(1);
    assertThat(second.conversationId()).isEqualTo("conv-A");
    assertThat(second.matchCount()).isEqualTo(4);
    assertThat(second.matchedMessages()).hasSize(3);
    assertThat(second.matchedMessages())
        .extracting(m -> m.messageId())
        .containsExactly("m1", "m2", "m3");
  }

  @Test
  @DisplayName("totalCount 는 전체 매칭 대화 수이고 results 는 요청 페이지 분량만 담는다")
  void shouldPaginateMatchedConversations() {
    Instant base = Instant.parse("2026-05-06T10:00:00Z");
    Conversation c1 = conversation("conv-1", "대화1", false, base.plus(3, ChronoUnit.HOURS));
    Conversation c2 = conversation("conv-2", "대화2", false, base.plus(2, ChronoUnit.HOURS));
    Conversation c3 = conversation("conv-3", "대화3", false, base.plus(1, ChronoUnit.HOURS));
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.findByUserIdAndDeletedAtIsNull("user-001"))
        .thenReturn(List.of(c1, c2, c3));
    when(messageRepository.searchActiveByConversationIdsAndContent(anyCollection(), anyString()))
        .thenReturn(
            List.of(
                message("m1", "conv-1", MessageRole.user, "S3 하나"),
                message("m2", "conv-2", MessageRole.user, "S3 둘"),
                message("m3", "conv-3", MessageRole.user, "S3 셋")));

    ConversationSearchResponse response = conversationSearchService.search("S3", 1, 2);

    assertThat(response.totalCount()).isEqualTo(3);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.results())
        .extracting(ConversationSearchResultResponse::conversationId)
        .containsExactly("conv-3");
  }

  @Test
  @DisplayName("검색어는 정규식 메타문자를 escape 한 뒤 저장소에 전달한다")
  void shouldPassEscapedQueryToRepository() {
    when(currentUserProvider.getUserId()).thenReturn("user-001");
    when(conversationRepository.findByUserIdAndDeletedAtIsNull("user-001"))
        .thenReturn(List.of(conversation("conv-1", "대화", false, Instant.now())));
    when(messageRepository.searchActiveByConversationIdsAndContent(anyCollection(), anyString()))
        .thenReturn(List.of());

    conversationSearchService.search("a.b", 0, 20);

    ArgumentCaptor<String> regexCaptor = ArgumentCaptor.forClass(String.class);
    verify(messageRepository)
        .searchActiveByConversationIdsAndContent(eq(List.of("conv-1")), regexCaptor.capture());
    assertThat(regexCaptor.getValue()).isEqualTo("a\\.b");
  }
}

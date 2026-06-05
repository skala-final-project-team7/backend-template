package com.lina.bff.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.bff.chat.entity.Conversation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

@DataMongoTest
@ActiveProfiles("test")
class ConversationRepositoryTest {

  @Autowired private ConversationRepository conversationRepository;

  @BeforeEach
  void clean() {
    conversationRepository.deleteAll();
  }

  private Conversation conversation(String userId, String title, Instant lastMessageAt) {
    return Conversation.builder().userId(userId).title(title).lastMessageAt(lastMessageAt).build();
  }

  private Conversation conversation(
      String userId, String title, Instant lastMessageAt, boolean isPinned) {
    return Conversation.builder()
        .userId(userId)
        .title(title)
        .lastMessageAt(lastMessageAt)
        .isPinned(isPinned)
        .build();
  }

  @Test
  @DisplayName("새 대화의 isPinned 기본값은 false 이다")
  void shouldDefaultIsPinnedToFalse() {
    Conversation saved =
        conversationRepository.save(conversation("user-001", "기본 고정값 대화", Instant.now()));

    assertThat(saved.isPinned()).isFalse();
  }

  @Test
  @DisplayName("사용자별 활성 대화를 고정 우선, lastMessageAt 내림차순으로 페이징 조회한다")
  void shouldPageActiveConversationsByPinnedAndLastMessageAtDesc() {
    Instant base = Instant.now();
    conversationRepository.save(
        conversation("user-001", "오래된 대화", base.minus(3, ChronoUnit.HOURS)));
    conversationRepository.save(conversation("user-001", "최신 대화", base));
    conversationRepository.save(
        conversation("user-001", "고정 대화", base.minus(10, ChronoUnit.HOURS), true));
    conversationRepository.save(conversation("user-001", "중간 대화", base.minus(1, ChronoUnit.HOURS)));
    conversationRepository.save(conversation("user-999", "다른 사용자", base));

    var firstPage =
        conversationRepository.findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(
            "user-001", org.springframework.data.domain.PageRequest.of(0, 2));

    assertThat(firstPage.getTotalElements()).isEqualTo(4);
    assertThat(firstPage.getContent())
        .extracting(Conversation::getTitle)
        .containsExactly("고정 대화", "최신 대화");
  }

  @Test
  @DisplayName("soft delete 된 대화는 목록에서 제외된다")
  void shouldExcludeSoftDeletedConversations() {
    conversationRepository.save(conversation("user-001", "남는 대화", Instant.now()));
    Conversation deleted =
        conversationRepository.save(conversation("user-001", "삭제될 대화", Instant.now()));
    deleted.markDeleted();
    conversationRepository.save(deleted);

    List<Conversation> active =
        conversationRepository
            .findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(
                "user-001", org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent();

    assertThat(active).extracting(Conversation::getTitle).containsExactly("남는 대화");
    assertThat(
            conversationRepository.findByConversationIdAndDeletedAtIsNull(
                deleted.getConversationId()))
        .isEmpty();
  }
}

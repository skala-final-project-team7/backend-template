package com.lina.bff.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

@DataMongoTest
@ActiveProfiles("test")
class MessageContentSearchRepositoryTest {

  @Autowired private MessageRepository messageRepository;

  @BeforeEach
  void clean() {
    messageRepository.deleteAll();
  }

  private Message message(
      String conversationId, MessageRole role, String content, Instant createdAt) {
    return Message.builder()
        .conversationId(conversationId)
        .role(role)
        .content(content)
        .createdAt(createdAt)
        .build();
  }

  @Test
  @DisplayName("대화 집합 내 본문이 검색어와 매칭되는 활성 메시지를 createdAt 오름차순으로 반환한다")
  void shouldReturnMatchingMessagesOrderedByCreatedAtAsc() {
    Instant base = Instant.parse("2026-05-06T10:00:00Z");
    messageRepository.save(
        message("conv-1", MessageRole.assistant, "S3 정책 수정", base.plus(1, ChronoUnit.SECONDS)));
    messageRepository.save(message("conv-1", MessageRole.user, "S3 권한 오류", base));
    messageRepository.save(message("conv-1", MessageRole.user, "관련 없는 질문", base));

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(
            List.of("conv-1", "conv-2"), "S3");

    assertThat(matched).extracting(Message::getContent).containsExactly("S3 권한 오류", "S3 정책 수정");
  }

  @Test
  @DisplayName("대화 집합 밖(타 사용자 대화) 메시지는 매칭되어도 반환하지 않는다")
  void shouldNotReturnMessagesOutsideConversationIdSet() {
    Instant now = Instant.now();
    messageRepository.save(message("conv-mine", MessageRole.user, "S3 권한 오류", now));
    messageRepository.save(message("conv-other", MessageRole.user, "S3 권한 오류", now));

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(List.of("conv-mine"), "S3");

    assertThat(matched).extracting(Message::getConversationId).containsExactly("conv-mine");
  }

  @Test
  @DisplayName("soft delete 된 메시지는 검색 결과에서 제외된다")
  void shouldExcludeSoftDeletedMessages() {
    Instant now = Instant.now();
    messageRepository.save(message("conv-1", MessageRole.user, "남는 S3 질문", now));
    Message deleted = messageRepository.save(message("conv-1", MessageRole.user, "삭제된 S3 질문", now));
    deleted.markDeleted();
    messageRepository.save(deleted);

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(List.of("conv-1"), "S3");

    assertThat(matched).extracting(Message::getContent).containsExactly("남는 S3 질문");
  }

  @Test
  @DisplayName("검색은 대소문자를 무시한다")
  void shouldMatchCaseInsensitively() {
    messageRepository.save(message("conv-1", MessageRole.user, "Hello S3 World", Instant.now()));

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(List.of("conv-1"), "s3");

    assertThat(matched).hasSize(1);
  }

  @Test
  @DisplayName("escape 된 검색어는 정규식 메타문자가 아닌 리터럴로 매칭된다")
  void shouldMatchEscapedQueryAsLiteral() {
    messageRepository.save(message("conv-1", MessageRole.user, "버전 a.b 입니다", Instant.now()));
    messageRepository.save(message("conv-1", MessageRole.user, "버전 axb 입니다", Instant.now()));

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(List.of("conv-1"), "a\\.b");

    assertThat(matched).extracting(Message::getContent).containsExactly("버전 a.b 입니다");
  }
}

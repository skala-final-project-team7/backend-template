package com.lina.bff.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.MessageSource;
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
class MessageRepositoryTest {

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
  @DisplayName("대화별 활성 메시지를 createdAt 오름차순(질문→답변)으로 조회한다")
  void shouldReturnMessageHistoryOrderedByCreatedAtAsc() {
    Instant base = Instant.now();
    messageRepository.save(
        message("conv-1", MessageRole.ASSISTANT, "답변", base.plus(1, ChronoUnit.SECONDS)));
    messageRepository.save(message("conv-1", MessageRole.USER, "질문", base));
    messageRepository.save(message("conv-2", MessageRole.USER, "다른 대화 질문", base));

    List<Message> history =
        messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1");

    assertThat(history).extracting(Message::getContent).containsExactly("질문", "답변");
  }

  @Test
  @DisplayName("soft delete 된 메시지는 이력에서 제외된다")
  void shouldExcludeSoftDeletedMessages() {
    messageRepository.save(message("conv-1", MessageRole.USER, "남는 질문", Instant.now()));
    Message deleted =
        messageRepository.save(message("conv-1", MessageRole.ASSISTANT, "삭제될 답변", Instant.now()));
    deleted.markDeleted();
    messageRepository.save(deleted);

    List<Message> history =
        messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1");

    assertThat(history).extracting(Message::getContent).containsExactly("남는 질문");
  }

  @Test
  @DisplayName("Message 의 sources 배열이 내장된 채로 저장·조회된다")
  void shouldPersistSourcesAsEmbeddedArray() {
    MessageSource source =
        MessageSource.builder()
            .title("S3 트러블슈팅 가이드")
            .pageId("12345")
            .spaceId("98310")
            .spaceName("Cloud Control Center")
            .url("https://confluence.example.com/pages/12345")
            .relevanceScore(0.92)
            .build();
    Message saved =
        messageRepository.save(
            Message.builder()
                .conversationId("conv-1")
                .role(MessageRole.ASSISTANT)
                .content("S3 권한 오류는 IAM 정책을 수정하여 해결합니다")
                .sources(List.of(source))
                .createdAt(Instant.now())
                .build());

    Message reloaded = messageRepository.findById(saved.getMessageId()).orElseThrow();

    assertThat(reloaded.getSources())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.getTitle()).isEqualTo("S3 트러블슈팅 가이드");
              assertThat(s.getPageId()).isEqualTo("12345");
              assertThat(s.getRelevanceScore()).isEqualTo(0.92);
            });
  }
}

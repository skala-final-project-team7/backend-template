package com.lina.bff.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MessageRepositoryTest {

  @Autowired private MessageRepository messageRepository;

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
  @DisplayName("대화별 활성 메시지를 created_at 오름차순(질문→답변)으로 조회한다")
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
    messageRepository.saveAndFlush(deleted);

    List<Message> history =
        messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc("conv-1");

    assertThat(history).extracting(Message::getContent).containsExactly("남는 질문");
  }
}

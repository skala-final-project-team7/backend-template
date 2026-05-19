package com.lina.bff.feedback.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.entity.FeedbackRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FeedbackRepositoryTest {

  @Autowired private FeedbackRepository feedbackRepository;

  private Feedback feedback(String messageId, FeedbackRating rating) {
    return Feedback.builder().messageId(messageId).rating(rating).build();
  }

  @Test
  @DisplayName("메시지당 피드백은 1건만 허용된다(uniq_feedbacks_message)")
  void shouldAllowOnlyOneFeedbackPerMessage() {
    feedbackRepository.saveAndFlush(feedback("msg-001", FeedbackRating.LIKE));

    assertThatThrownBy(
            () -> feedbackRepository.saveAndFlush(feedback("msg-001", FeedbackRating.DISLIKE)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("message_id 로 기존 피드백을 단건 조회한다")
  void shouldFindExistingFeedbackByMessageId() {
    feedbackRepository.saveAndFlush(feedback("msg-002", FeedbackRating.LIKE));

    assertThat(feedbackRepository.findByMessageId("msg-002")).isPresent();
    assertThat(feedbackRepository.findByMessageId("absent")).isEmpty();
  }
}

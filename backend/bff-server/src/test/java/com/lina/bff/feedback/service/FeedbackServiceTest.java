package com.lina.bff.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.feedback.dto.CreateFeedbackRequest;
import com.lina.bff.feedback.dto.FeedbackResponse;
import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.entity.FeedbackRating;
import com.lina.bff.feedback.repository.FeedbackRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

  @Mock private FeedbackRepository feedbackRepository;
  @Mock private MessageRepository messageRepository;

  @InjectMocks private FeedbackService feedbackService;

  @Test
  @DisplayName("피드백 응답 DTO 의 createdAt 은 ZonedDateTime 으로 노출한다")
  void shouldExposeFeedbackTimestampAsZonedDateTimeField() {
    assertThat(recordComponentType(FeedbackResponse.class, "createdAt"))
        .isEqualTo(ZonedDateTime.class);
  }

  @Test
  @DisplayName("신규 피드백은 created=true 로 저장하고 KST createdAt 을 반환한다")
  void shouldRegisterNewFeedback() {
    when(messageRepository.existsByMessageIdAndDeletedAtIsNull("msg-001")).thenReturn(true);
    when(feedbackRepository.findByMessageId("msg-001")).thenReturn(Optional.empty());
    when(feedbackRepository.save(any(Feedback.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FeedbackService.FeedbackResult result =
        feedbackService.registerFeedback(
            "msg-001", new CreateFeedbackRequest(FeedbackRating.LIKE, "정확한 답변이었어요"));

    assertThat(result.created()).isTrue();
    assertThat(result.response().messageId()).isEqualTo("msg-001");
    assertThat(result.response().rating()).isEqualTo(FeedbackRating.LIKE);
    assertThat(result.response().feedbackId()).isNotBlank();
    assertThat(result.response().createdAt().getZone().getId()).isEqualTo("Asia/Seoul");

    ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
    verify(feedbackRepository).save(captor.capture());
    assertThat(captor.getValue().getMessageId()).isEqualTo("msg-001");
    assertThat(captor.getValue().getRating()).isEqualTo(FeedbackRating.LIKE);
    assertThat(captor.getValue().getComment()).isEqualTo("정확한 답변이었어요");
  }

  @Test
  @DisplayName("기존 피드백은 created=false 로 갱신하며 feedbackId 와 최초 createdAt 을 유지한다")
  void shouldUpdateExistingFeedback() {
    Instant firstCreatedAt = Instant.parse("2026-05-06T10:00:00Z");
    Feedback existing =
        Feedback.builder()
            .feedbackId("fb-existing")
            .messageId("msg-002")
            .rating(FeedbackRating.LIKE)
            .comment("처음 코멘트")
            .createdAt(firstCreatedAt)
            .build();
    when(messageRepository.existsByMessageIdAndDeletedAtIsNull("msg-002")).thenReturn(true);
    when(feedbackRepository.findByMessageId("msg-002")).thenReturn(Optional.of(existing));
    when(feedbackRepository.save(any(Feedback.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FeedbackService.FeedbackResult result =
        feedbackService.registerFeedback(
            "msg-002", new CreateFeedbackRequest(FeedbackRating.DISLIKE, "다시 보니 부정확해요"));

    assertThat(result.created()).isFalse();
    assertThat(result.response().feedbackId()).isEqualTo("fb-existing");
    assertThat(result.response().rating()).isEqualTo(FeedbackRating.DISLIKE);
    assertThat(result.response().createdAt().toInstant()).isEqualTo(firstCreatedAt);

    ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
    verify(feedbackRepository).save(captor.capture());
    assertThat(captor.getValue().getFeedbackId()).isEqualTo("fb-existing");
    assertThat(captor.getValue().getRating()).isEqualTo(FeedbackRating.DISLIKE);
    assertThat(captor.getValue().getComment()).isEqualTo("다시 보니 부정확해요");
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(firstCreatedAt);
  }

  @Test
  @DisplayName("존재하지 않거나 삭제된 메시지에는 피드백을 등록하지 않는다")
  void shouldRejectFeedbackWhenMessageMissingOrDeleted() {
    when(messageRepository.existsByMessageIdAndDeletedAtIsNull("msg-missing")).thenReturn(false);

    assertThatThrownBy(
            () ->
                feedbackService.registerFeedback(
                    "msg-missing", new CreateFeedbackRequest(FeedbackRating.LIKE, null)))
        .isInstanceOf(BizException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(feedbackRepository, never()).save(any(Feedback.class));
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

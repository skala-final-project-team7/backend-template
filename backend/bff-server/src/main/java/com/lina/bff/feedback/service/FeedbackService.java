package com.lina.bff.feedback.service;

import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.feedback.dto.CreateFeedbackRequest;
import com.lina.bff.feedback.dto.FeedbackResponse;
import com.lina.bff.feedback.entity.Feedback;
import com.lina.bff.feedback.repository.FeedbackRepository;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드백 등록/갱신 도메인 서비스(`docs/api-spec.md` §1-3, Feature 6).
 *
 * <p>메시지당 1건만 허용하며(`uniq_feedbacks_message`) 재등록은 동일 문서 upsert 로 처리한다. 신규/갱신 여부는 {@link
 * FeedbackResult} 의 {@code created} 로 전달되어 Controller 가 HTTP status(신규 201 / 갱신 200)를 결정한다.
 */
@Service
public class FeedbackService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final FeedbackRepository feedbackRepository;
  private final MessageRepository messageRepository;

  public FeedbackService(
      FeedbackRepository feedbackRepository, MessageRepository messageRepository) {
    this.feedbackRepository = feedbackRepository;
    this.messageRepository = messageRepository;
  }

  /** 피드백 등록/갱신 결과. {@code created=true} 면 신규(201), {@code false} 면 갱신(200). */
  public record FeedbackResult(boolean created, FeedbackResponse response) {}

  @Transactional
  public FeedbackResult registerFeedback(String messageId, CreateFeedbackRequest request) {
    if (!messageRepository.existsByMessageIdAndDeletedAtIsNull(messageId)) {
      throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 메시지를 찾을 수 없습니다.");
    }

    Optional<Feedback> existing = feedbackRepository.findByMessageId(messageId);
    if (existing.isPresent()) {
      Feedback feedback = existing.get();
      feedback.update(request.rating(), request.comment());
      Feedback saved = feedbackRepository.save(feedback);
      return new FeedbackResult(false, toResponse(saved));
    }

    Feedback feedback =
        Feedback.builder()
            .messageId(messageId)
            .rating(request.rating())
            .comment(request.comment())
            .build();
    Feedback saved = feedbackRepository.save(feedback);
    return new FeedbackResult(true, toResponse(saved));
  }

  private FeedbackResponse toResponse(Feedback feedback) {
    return new FeedbackResponse(
        feedback.getFeedbackId(),
        feedback.getMessageId(),
        feedback.getRating(),
        feedback.getCreatedAt().atZone(KST));
  }
}

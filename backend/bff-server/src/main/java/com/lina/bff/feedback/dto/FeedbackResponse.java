package com.lina.bff.feedback.dto;

import com.lina.bff.feedback.entity.FeedbackRating;
import java.time.ZonedDateTime;

/**
 * 피드백 등록/갱신 응답 본문(`docs/api-spec.md` §1-3).
 *
 * <p>{@code createdAt} 은 KST(`Asia/Seoul`, `+09:00`)로 직렬화한다(확정된 결정 #6).
 */
public record FeedbackResponse(
    String feedbackId, String messageId, FeedbackRating rating, ZonedDateTime createdAt) {}

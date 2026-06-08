package com.lina.bff.feedback.dto;

import com.lina.bff.feedback.entity.FeedbackRating;
import jakarta.validation.constraints.NotNull;

/**
 * 피드백 등록/갱신 요청 본문(`docs/api-spec.md` §1-3).
 *
 * <p>{@code rating} 은 필수이며 {@code LIKE}/{@code DISLIKE} UPPER 표기만 허용한다. {@code comment} 는 선택이다.
 */
public record CreateFeedbackRequest(@NotNull FeedbackRating rating, String comment) {}

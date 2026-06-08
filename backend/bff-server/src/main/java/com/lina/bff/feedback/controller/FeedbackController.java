package com.lina.bff.feedback.controller;

import com.lina.bff.feedback.dto.CreateFeedbackRequest;
import com.lina.bff.feedback.dto.FeedbackResponse;
import com.lina.bff.feedback.service.FeedbackService;
import com.lina.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class FeedbackController {

  private final FeedbackService feedbackService;

  public FeedbackController(FeedbackService feedbackService) {
    this.feedbackService = feedbackService;
  }

  @PostMapping("/{messageId}/feedback")
  public ResponseEntity<ApiResponse<FeedbackResponse>> registerFeedback(
      @PathVariable String messageId, @Valid @RequestBody CreateFeedbackRequest request) {
    FeedbackService.FeedbackResult result = feedbackService.registerFeedback(messageId, request);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    String message = result.created() ? "피드백 등록 성공" : "피드백 수정 성공";
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(true, status.value(), result.response(), message));
  }
}

package com.lina.bff.feedback.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.feedback.dto.CreateFeedbackRequest;
import com.lina.bff.feedback.dto.FeedbackResponse;
import com.lina.bff.feedback.entity.FeedbackRating;
import com.lina.bff.feedback.service.FeedbackService;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import com.lina.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeedbackControllerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private MockMvc mockMvc;

  @MockBean private FeedbackService feedbackService;

  @Test
  @DisplayName("POST /api/messages/{id}/feedback 신규 등록 시 201 Wrapper 와 KST createdAt 을 반환한다")
  void shouldCreateFeedback() throws Exception {
    when(feedbackService.registerFeedback(eq("msg-002"), any(CreateFeedbackRequest.class)))
        .thenReturn(
            new FeedbackService.FeedbackResult(
                true,
                new FeedbackResponse(
                    "fb-uuid-001",
                    "msg-002",
                    FeedbackRating.LIKE,
                    Instant.parse("2026-05-06T10:06:00Z").atZone(KST))));

    mockMvc
        .perform(
            post("/api/messages/msg-002/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"LIKE\",\"comment\":\"정확한 답변이었어요\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.message").value("피드백 등록 성공"))
        .andExpect(jsonPath("$.data.feedbackId").value("fb-uuid-001"))
        .andExpect(jsonPath("$.data.messageId").value("msg-002"))
        .andExpect(jsonPath("$.data.rating").value("LIKE"))
        .andExpect(jsonPath("$.data.createdAt").value("2026-05-06T19:06:00+09:00"));
  }

  @Test
  @DisplayName("POST /api/messages/{id}/feedback 갱신 시 200 Wrapper 를 반환한다")
  void shouldUpdateFeedback() throws Exception {
    when(feedbackService.registerFeedback(eq("msg-002"), any(CreateFeedbackRequest.class)))
        .thenReturn(
            new FeedbackService.FeedbackResult(
                false,
                new FeedbackResponse(
                    "fb-uuid-001",
                    "msg-002",
                    FeedbackRating.DISLIKE,
                    Instant.parse("2026-05-06T10:06:00Z").atZone(KST))));

    mockMvc
        .perform(
            post("/api/messages/msg-002/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"DISLIKE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("피드백 수정 성공"))
        .andExpect(jsonPath("$.data.rating").value("DISLIKE"));
  }

  @Test
  @DisplayName("rating 이 누락되면 400 ErrorResponse 를 반환한다")
  void shouldReturnBadRequestWhenRatingMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/messages/msg-002/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"평가 없음\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.data").doesNotExist());

    verifyNoInteractions(feedbackService);
  }

  @Test
  @DisplayName("rating 값이 잘못되면 400 ErrorResponse 를 반환한다")
  void shouldReturnBadRequestWhenRatingInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/messages/msg-002/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.data").doesNotExist());

    verifyNoInteractions(feedbackService);
  }

  @Test
  @DisplayName("존재하지 않는 메시지에 피드백 등록 시 404 ErrorResponse 를 반환한다")
  void shouldReturnNotFoundWhenMessageMissing() throws Exception {
    when(feedbackService.registerFeedback(eq("msg-missing"), any(CreateFeedbackRequest.class)))
        .thenThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 메시지를 찾을 수 없습니다."));

    mockMvc
        .perform(
            post("/api/messages/msg-missing/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"LIKE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 메시지를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}

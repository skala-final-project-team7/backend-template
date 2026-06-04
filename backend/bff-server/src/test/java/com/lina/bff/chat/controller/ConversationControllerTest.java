package com.lina.bff.chat.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.chat.dto.ConversationListResponse;
import com.lina.bff.chat.dto.ConversationSummaryResponse;
import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.dto.UpdateConversationRequest;
import com.lina.bff.chat.dto.UpdateConversationResponse;
import com.lina.bff.chat.service.ConversationService;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import com.lina.common.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ConversationControllerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private MockMvc mockMvc;

  @MockBean private ConversationService conversationService;

  @Test
  @DisplayName("POST /api/conversations 는 201 Wrapper 와 KST createdAt 을 반환한다")
  void shouldCreateConversation() throws Exception {
    when(conversationService.createConversation())
        .thenReturn(
            new CreateConversationResponse(
                "conv-1", "새 대화", false, Instant.parse("2026-05-06T10:00:00Z").atZone(KST)));

    mockMvc
        .perform(post("/api/conversations"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.message").value("새 대화 생성 성공"))
        .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
        .andExpect(jsonPath("$.data.title").value("새 대화"))
        .andExpect(jsonPath("$.data.isPinned").value(false))
        .andExpect(jsonPath("$.data.createdAt").value("2026-05-06T19:00:00+09:00"));
  }

  @Test
  @DisplayName("POST /api/conversations 생성 실패 시 공통 ErrorResponse 를 반환한다")
  void shouldReturnErrorResponseWhenCreateConversationFails() throws Exception {
    when(conversationService.createConversation())
        .thenThrow(new BizException(ErrorCode.INVALID_REQUEST, "현재 사용자 식별자가 없습니다."));

    mockMvc
        .perform(post("/api/conversations"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("현재 사용자 식별자가 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("GET /api/conversations 는 200 Wrapper 와 KST lastMessageAt 목록을 반환한다")
  void shouldListConversations() throws Exception {
    when(conversationService.listConversations(0, 20))
        .thenReturn(
            new ConversationListResponse(
                List.of(
                    new ConversationSummaryResponse(
                        "conv-1", "고정 대화", Instant.parse("2026-05-06T09:00:00Z").atZone(KST), true),
                    new ConversationSummaryResponse(
                        "conv-2",
                        "최신 대화",
                        Instant.parse("2026-05-06T10:00:00Z").atZone(KST),
                        false)),
                2,
                0,
                20));

    mockMvc
        .perform(get("/api/conversations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("대화 목록 조회 성공"))
        .andExpect(jsonPath("$.data.totalCount").value(2))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.conversations[0].conversationId").value("conv-1"))
        .andExpect(jsonPath("$.data.conversations[0].title").value("고정 대화"))
        .andExpect(jsonPath("$.data.conversations[0].isPinned").value(true))
        .andExpect(
            jsonPath("$.data.conversations[0].lastMessageAt").value("2026-05-06T18:00:00+09:00"));
  }

  @Test
  @DisplayName("PATCH /api/conversations/{id} 는 제목과 고정 여부를 부분 수정한다")
  void shouldUpdateConversation() throws Exception {
    when(conversationService.updateConversation(
            "conv-1", new UpdateConversationRequest("수정된 대화 제목", true)))
        .thenReturn(
            new UpdateConversationResponse(
                "conv-1", "수정된 대화 제목", true, Instant.parse("2026-05-06T10:10:00Z").atZone(KST)));

    mockMvc
        .perform(
            patch("/api/conversations/conv-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정된 대화 제목\",\"isPinned\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("대화 수정 성공"))
        .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
        .andExpect(jsonPath("$.data.title").value("수정된 대화 제목"))
        .andExpect(jsonPath("$.data.isPinned").value(true))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-05-06T19:10:00+09:00"));
  }

  @Test
  @DisplayName("PATCH /api/conversations/{id} 수정 실패 시 공통 ErrorResponse 를 반환한다")
  void shouldReturnErrorResponseWhenUpdateConversationFails() throws Exception {
    when(conversationService.updateConversation(
            "conv-1", new UpdateConversationRequest(null, null)))
        .thenThrow(new BizException(ErrorCode.INVALID_REQUEST, "title 또는 isPinned 중 하나는 필요합니다."));

    mockMvc
        .perform(
            patch("/api/conversations/conv-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("title 또는 isPinned 중 하나는 필요합니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("PATCH /api/conversations/{id} 대화가 없거나 삭제된 경우 404 ErrorResponse 를 반환한다")
  void shouldReturnNotFoundWhenUpdateMissingOrDeletedConversation() throws Exception {
    when(conversationService.updateConversation(
            "conv-missing", new UpdateConversationRequest("수정된 대화 제목", null)))
        .thenThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));

    mockMvc
        .perform(
            patch("/api/conversations/conv-missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정된 대화 제목\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 대화를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("DELETE /api/conversations/{id} 는 soft delete 후 data null Wrapper 를 반환한다")
  void shouldDeleteConversation() throws Exception {
    mockMvc
        .perform(delete("/api/conversations/conv-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("대화 삭제 성공"))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("DELETE /api/conversations/{id} 삭제 실패 시 공통 ErrorResponse 를 반환한다")
  void shouldReturnErrorResponseWhenDeleteConversationFails() throws Exception {
    doThrow(new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."))
        .when(conversationService)
        .deleteConversation("conv-1");

    mockMvc
        .perform(delete("/api/conversations/conv-1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("해당 대화를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }
}

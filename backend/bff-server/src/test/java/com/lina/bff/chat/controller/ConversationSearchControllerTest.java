package com.lina.bff.chat.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.chat.dto.ConversationSearchResponse;
import com.lina.bff.chat.dto.ConversationSearchResultResponse;
import com.lina.bff.chat.dto.MatchedMessageResponse;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.service.ConversationSearchService;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConversationSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ConversationSearchControllerTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private MockMvc mockMvc;

  @MockBean private ConversationSearchService conversationSearchService;

  @Test
  @DisplayName("GET /api/conversations/search 는 200 Wrapper 와 매칭 결과 구조를 KST 로 반환한다")
  void shouldSearchConversations() throws Exception {
    MatchedMessageResponse matched =
        new MatchedMessageResponse(
            "msg-2",
            MessageRole.assistant,
            "...IAM 정책을 수정하여 S3 권한 오류를 해결했습니다...",
            List.of(new int[] {14, 16}),
            Instant.parse("2026-05-06T10:00:05Z").atZone(KST));
    ConversationSearchResultResponse result =
        new ConversationSearchResultResponse(
            "conv-1",
            "S3 권한 오류 해결 방법",
            Instant.parse("2026-05-06T10:05:00Z").atZone(KST),
            false,
            List.of(matched),
            3);
    when(conversationSearchService.search("S3", 0, 20))
        .thenReturn(new ConversationSearchResponse(List.of(result), 5, 0, 20));

    mockMvc
        .perform(get("/api/conversations/search").param("q", "S3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").value("대화 검색 성공"))
        .andExpect(jsonPath("$.data.totalCount").value(5))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.results[0].conversationId").value("conv-1"))
        .andExpect(jsonPath("$.data.results[0].title").value("S3 권한 오류 해결 방법"))
        .andExpect(jsonPath("$.data.results[0].isPinned").value(false))
        .andExpect(jsonPath("$.data.results[0].lastMessageAt").value("2026-05-06T19:05:00+09:00"))
        .andExpect(jsonPath("$.data.results[0].matchCount").value(3))
        .andExpect(jsonPath("$.data.results[0].matchedMessages[0].messageId").value("msg-2"))
        .andExpect(jsonPath("$.data.results[0].matchedMessages[0].role").value("assistant"))
        .andExpect(
            jsonPath("$.data.results[0].matchedMessages[0].snippet")
                .value("...IAM 정책을 수정하여 S3 권한 오류를 해결했습니다..."))
        .andExpect(jsonPath("$.data.results[0].matchedMessages[0].matchPositions[0][0]").value(14))
        .andExpect(jsonPath("$.data.results[0].matchedMessages[0].matchPositions[0][1]").value(16))
        .andExpect(
            jsonPath("$.data.results[0].matchedMessages[0].createdAt")
                .value("2026-05-06T19:00:05+09:00"));
  }

  @Test
  @DisplayName("q 검증 실패 시 400 INVALID_SEARCH_QUERY ErrorResponse 를 반환한다")
  void shouldReturnInvalidSearchQueryWhenQueryInvalid() throws Exception {
    when(conversationSearchService.search("", 0, 20))
        .thenThrow(new BizException(ErrorCode.INVALID_SEARCH_QUERY, "검색어는 2~50자여야 합니다."));

    mockMvc
        .perform(get("/api/conversations/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_SEARCH_QUERY"))
        .andExpect(jsonPath("$.message").value("검색어는 2~50자여야 합니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("page 파라미터 형식이 잘못되면 400 INVALID_REQUEST 이고 서비스를 호출하지 않는다")
  void shouldReturnBadRequestWhenPageFormatInvalid() throws Exception {
    mockMvc
        .perform(get("/api/conversations/search").param("q", "S3").param("page", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.data").doesNotExist());

    verifyNoInteractions(conversationSearchService);
  }
}

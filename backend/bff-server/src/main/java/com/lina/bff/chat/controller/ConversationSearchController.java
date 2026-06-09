package com.lina.bff.chat.controller;

import com.lina.bff.chat.dto.ConversationSearchResponse;
import com.lina.bff.chat.service.ConversationSearchService;
import com.lina.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대화 검색(Feature 7) 진입점. {@code GET /api/conversations/search}.
 *
 * <p>{@code q} 검증·권한 격리·snippet 추출 등 검색 로직은 {@link ConversationSearchService} 가 담당한다. Controller 는
 * 파라미터 바인딩과 공통 Wrapper 변환만 수행한다.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationSearchController {

  private final ConversationSearchService conversationSearchService;

  @GetMapping("/search")
  public ResponseEntity<ApiResponse<ConversationSearchResponse>> searchConversations(
      @RequestParam(name = "q", defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    ConversationSearchResponse response = conversationSearchService.search(q, page, size);
    return ResponseEntity.ok(ApiResponse.success(response, "대화 검색 성공"));
  }
}

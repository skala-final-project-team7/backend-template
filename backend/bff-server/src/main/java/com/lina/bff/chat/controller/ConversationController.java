package com.lina.bff.chat.controller;

import com.lina.bff.chat.dto.ConversationListResponse;
import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.dto.MessageHistoryResponse;
import com.lina.bff.chat.dto.UpdateConversationRequest;
import com.lina.bff.chat.dto.UpdateConversationResponse;
import com.lina.bff.chat.service.ConversationService;
import com.lina.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationService conversationService;

  @PostMapping
  public ResponseEntity<ApiResponse<CreateConversationResponse>> createConversation() {
    CreateConversationResponse response = conversationService.createConversation();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ApiResponse<>(true, HttpStatus.CREATED.value(), response, "새 대화 생성 성공"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<ConversationListResponse>> listConversations(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    ConversationListResponse response = conversationService.listConversations(page, size);
    return ResponseEntity.ok(ApiResponse.success(response, "대화 목록 조회 성공"));
  }

  @GetMapping("/{conversationId}/messages")
  public ResponseEntity<ApiResponse<MessageHistoryResponse>> getMessageHistory(
      @PathVariable String conversationId) {
    MessageHistoryResponse response = conversationService.getMessageHistory(conversationId);
    return ResponseEntity.ok(ApiResponse.success(response, "메시지 이력 조회 성공"));
  }

  @PatchMapping("/{conversationId}")
  public ResponseEntity<ApiResponse<UpdateConversationResponse>> updateConversation(
      @PathVariable String conversationId, @RequestBody UpdateConversationRequest request) {
    UpdateConversationResponse response =
        conversationService.updateConversation(conversationId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "대화 수정 성공"));
  }

  @DeleteMapping("/{conversationId}")
  public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable String conversationId) {
    conversationService.deleteConversation(conversationId);
    return ResponseEntity.ok(ApiResponse.success(null, "대화 삭제 성공"));
  }
}

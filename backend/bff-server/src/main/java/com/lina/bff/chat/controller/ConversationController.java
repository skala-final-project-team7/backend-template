package com.lina.bff.chat.controller;

import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.service.ConversationService;
import com.lina.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

  private final ConversationService conversationService;

  public ConversationController(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CreateConversationResponse>> createConversation() {
    CreateConversationResponse response = conversationService.createConversation();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ApiResponse<>(true, HttpStatus.CREATED.value(), response, "새 대화 생성 성공"));
  }
}

package com.lina.bff.chat.controller;

import com.lina.bff.chat.dto.ChatRequest;
import com.lina.bff.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : FE 챗봇 질의를 받아 RAG Pipeline SSE 스트림을 중계하는 컨트롤러.
 *           SSE 응답은 공통 ApiResponse Wrapper 를 적용하지 않는다.
 * 작성일 : 2026-06-07
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-07, 최초 작성, POST /api/conversations/{conversationId}/chat 엔드포인트 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — Virtual Threads 사용
 *   - Spring Boot 3.3.x / Spring MVC 6.1.x (SseEmitter)
 * --------------------------------------------------
 * </pre>
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatController {

  private final ChatService chatService;

  /**
   * 사용자 질문을 RAG Pipeline 으로 전달하고 SSE 스트림을 반환한다.
   *
   * @param conversationId 질의가 속한 대화 식별자
   * @param request 사용자 질문 요청
   * @return SseEmitter — Wrapper 미적용 SSE 응답
   */
  @PostMapping(path = "/{conversationId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chat(
      @PathVariable String conversationId, @Valid @RequestBody ChatRequest request) {
    return chatService.streamChat(conversationId, request.question());
  }
}

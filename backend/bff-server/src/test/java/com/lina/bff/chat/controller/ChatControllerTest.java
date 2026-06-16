package com.lina.bff.chat.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.chat.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private ChatService chatService;

  @Test
  @DisplayName("POST /api/conversations/{conversationId}/chat 은 Wrapper 없이 SseEmitter 를 반환한다")
  void shouldReturnSseEmitterWithoutWrapper() throws Exception {
    SseEmitter emitter = new SseEmitter(1000L);
    when(chatService.streamChat("conv-1", "질문")).thenReturn(emitter);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/conversations/conv-1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content("{\"question\":\"질문\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

    emitter.send(SseEmitter.event().name("token").data("{\"content\":\"답변\"}"));
    emitter.complete();

    mockMvc
        .perform(MockMvcRequestBuilders.asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
        .andExpect(header().string("Connection", "keep-alive"))
        .andExpect(header().string("X-Accel-Buffering", "no"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"isSuccess\""))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"code\""))));

    verify(chatService).streamChat("conv-1", "질문");
  }

  @Test
  @DisplayName("Controller 는 ChatService 가 보낸 SSE 이벤트 시퀀스를 그대로 응답한다")
  void shouldWriteSseEventsInServiceSequence() throws Exception {
    SseEmitter emitter = new SseEmitter(1000L);
    when(chatService.streamChat("conv-1", "질문")).thenReturn(emitter);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/conversations/conv-1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .content("{\"question\":\"질문\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

    emitter.send(SseEmitter.event().name("status").data("{\"phase\":\"searching\"}"));
    emitter.send(SseEmitter.event().name("token").data("{\"content\":\"답변\"}"));
    emitter.send(SseEmitter.event().name("done").data("{\"messageId\":\"msg-1\"}"));
    emitter.complete();

    String responseBody =
        mockMvc
            .perform(MockMvcRequestBuilders.asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

    org.assertj.core.api.Assertions.assertThat(responseBody)
        .containsSubsequence("event:status", "event:token", "event:done")
        .contains("{\"phase\":\"searching\"}", "{\"content\":\"답변\"}", "\"messageId\":\"msg-1\"");
  }
}

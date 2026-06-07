package com.lina.bff.chat.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.chat.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private ChatService chatService;

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
}

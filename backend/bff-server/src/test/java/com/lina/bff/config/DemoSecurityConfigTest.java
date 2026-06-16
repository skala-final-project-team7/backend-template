package com.lina.bff.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lina.bff.security.BffJwtVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = DemoSecurityConfigTest.TestPingController.class)
@Import({DemoSecurityConfig.class, DemoSecurityConfigTest.TestPingController.class})
@ActiveProfiles("demo")
class DemoSecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean
  private BffJwtVerifier jwtVerifier;

  @Test
  @DisplayName("인증 없는 GET 요청도 permitAll 로 200 응답한다")
  void shouldPermitAnonymousGet() throws Exception {
    mockMvc
        .perform(get("/__test/demo-security/ping"))
        .andExpect(status().isOk())
        .andExpect(content().string("pong"));
  }

  @Test
  @DisplayName("CSRF 토큰 없는 POST 요청도 통과해야 한다 (CSRF 비활성)")
  void shouldPermitAnonymousPostWithoutCsrfToken() throws Exception {
    mockMvc
        .perform(post("/__test/demo-security/echo"))
        .andExpect(status().isOk())
        .andExpect(content().string("echo"));
  }

  @RestController
  static class TestPingController {

    @GetMapping("/__test/demo-security/ping")
    String ping() {
      return "pong";
    }

    @PostMapping("/__test/demo-security/echo")
    String echo() {
      return "echo";
    }
  }
}

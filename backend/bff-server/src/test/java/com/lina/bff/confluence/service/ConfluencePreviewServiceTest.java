package com.lina.bff.confluence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.confluence.client.AuthServerConfluenceClient;
import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Feature P1 — 현재 사용자 userId 를 확보해 내부 프록시 client 에 전달하는 오케스트레이션 검증. */
@ExtendWith(MockitoExtension.class)
class ConfluencePreviewServiceTest {

  private static final String USER_ID = "712020:abc";
  private static final String PAGE_ID = "12345";

  @Mock private CurrentUserProvider currentUserProvider;
  @Mock private AuthServerConfluenceClient authServerConfluenceClient;

  @Test
  @DisplayName("CurrentUserProvider 의 userId 로 client 를 호출하고 결과를 그대로 반환한다")
  void shouldResolveUserIdAndDelegateToClient() {
    ConfluencePagePreviewResponse preview =
        new ConfluencePagePreviewResponse(
            PAGE_ID,
            "S3 트러블슈팅 가이드",
            "Cloud Control Center",
            "Platform Team",
            "2026-04-15T18:30:00+09:00",
            List.of("Cloud Control Center", "S3 트러블슈팅 가이드"),
            "https://team.atlassian.net/wiki/spaces/CCC/pages/12345/S3",
            "<h1>S3</h1>");
    given(currentUserProvider.getUserId()).willReturn(USER_ID);
    given(authServerConfluenceClient.fetchPagePreview(USER_ID, PAGE_ID)).willReturn(preview);

    ConfluencePreviewService service =
        new ConfluencePreviewService(currentUserProvider, authServerConfluenceClient);
    ConfluencePagePreviewResponse result = service.getPreview(PAGE_ID);

    assertThat(result).isSameAs(preview);
    verify(authServerConfluenceClient).fetchPagePreview(USER_ID, PAGE_ID);
  }
}

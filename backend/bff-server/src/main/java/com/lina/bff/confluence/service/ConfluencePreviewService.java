package com.lina.bff.confluence.service;

import com.lina.bff.config.CurrentUserProvider;
import com.lina.bff.confluence.client.AuthServerConfluenceClient;
import com.lina.bff.confluence.dto.ConfluencePagePreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : Confluence 미리보기 오케스트레이션(Feature P1, api-spec §4-3). 현재 요청 사용자 userId(accountId)를
 *           CurrentUserProvider 로 확보해 auth-server 내부 프록시 client 에 전달한다. ACL 은 사용자 본인 토큰
 *           호출로 Confluence 가 자연 강제한다(접근 불가=404).
 * 작성일 : 2026-06-18
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-18, 최초 작성, 4단계 Feature P1 — Confluence 미리보기 BFF 공개 endpoint
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ConfluencePreviewService {

  private final CurrentUserProvider currentUserProvider;
  private final AuthServerConfluenceClient authServerConfluenceClient;

  public ConfluencePagePreviewResponse getPreview(String pageId) {
    String userId = currentUserProvider.getUserId();
    return authServerConfluenceClient.fetchPagePreview(userId, pageId);
  }
}

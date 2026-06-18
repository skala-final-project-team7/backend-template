package com.lina.bff.confluence.dto;

import java.util.List;

/**
 * §4-3 Confluence 페이지 미리보기 응답(ApiResponse.data). auth-server 내부 프록시(P2)가 사용자 OAuth 토큰으로 조회·매핑한 결과를
 * 그대로 전달한다. BFF 는 Confluence 토큰을 보유하지 않으며 본 DTO 에도 토큰 필드가 없다. bodyViewValue 는 Confluence body.view
 * HTML 원문 passthrough 이며 sanitize 는 FE 책임이다(§4-3 보안 주의).
 *
 * @param pageId Confluence page ID
 * @param title 페이지 제목
 * @param spaceName 소속 스페이스명
 * @param authorName 최종 수정자 표시명
 * @param updatedAt 최종 수정 시각(KST ISO-8601 offset)
 * @param breadcrumbs [space.name, ...ancestors[].title, title] 경로
 * @param pageUrl 원본 페이지 absolute URL
 * @param bodyViewValue Confluence body.view.value HTML 원문(FE 가 sanitize)
 */
public record ConfluencePagePreviewResponse(
    String pageId,
    String title,
    String spaceName,
    String authorName,
    String updatedAt,
    List<String> breadcrumbs,
    String pageUrl,
    String bodyViewValue) {}

package com.lina.auth.internal.dto;

import java.util.List;

/**
 * §4-3 Confluence 페이지 미리보기 내부 응답(wrapper 미적용 raw JSON — BFF 가 공개 응답에서 wrapping). OAuth 토큰 등 민감 값을
 * 필드로 두지 않아 노출을 구조적으로 차단한다. bodyViewValue 는 Confluence body.view HTML 원문이며 sanitize 는 FE 책임이다(§4-3
 * 보안 주의).
 *
 * @param pageId Confluence page ID
 * @param title 페이지 제목
 * @param spaceName 소속 스페이스명
 * @param authorName 최종 수정자 표시명
 * @param updatedAt 최종 수정 시각(KST ISO-8601 offset — api-spec Common 시간 표기)
 * @param breadcrumbs [space.name, ...ancestors[].title, title] 경로
 * @param pageUrl 원본 페이지 absolute URL(_links.base + _links.webui)
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

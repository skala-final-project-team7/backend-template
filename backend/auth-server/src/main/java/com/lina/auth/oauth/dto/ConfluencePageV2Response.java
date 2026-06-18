package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Confluence v2 pages API(`/wiki/api/v2/pages/{id}?body-format=view`) 응답 매핑 DTO. 미리보기(§4-3) 구성에 필요한
 * 필드만 받고 나머지는 무시한다. granular scope(`read:page:confluence`) 전용 — v1 content API 는 동일 토큰에 401 을 반환하므로
 * v2 를 사용한다. 본문(body.view.value)은 HTML 원문이며 sanitize 는 FE 책임이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluencePageV2Response(
    String id,
    String title,
    String spaceId,
    Version version,
    Body body,
    @JsonProperty("_links") Links links) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Version(String createdAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Body(View view) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record View(String value) {}
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Links(String base, String webui) {}
}

package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Confluence v2 spaces API(`/wiki/api/v2/spaces/{id}`) 응답 매핑 DTO. 미리보기 breadcrumbs/spaceName 구성용으로
 * `name` 만 사용한다(나머지 무시).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfluenceSpaceV2Response(String id, String key, String name) {}

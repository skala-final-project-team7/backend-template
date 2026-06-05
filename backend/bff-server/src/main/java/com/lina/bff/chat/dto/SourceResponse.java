package com.lina.bff.chat.dto;

import java.time.ZonedDateTime;

public record SourceResponse(
    String title,
    String pageId,
    String spaceId,
    String spaceName,
    String url,
    ZonedDateTime sourceUpdatedAt,
    Double relevanceScore) {}

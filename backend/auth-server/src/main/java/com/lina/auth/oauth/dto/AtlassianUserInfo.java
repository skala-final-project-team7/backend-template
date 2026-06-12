package com.lina.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** user-info(/me) 응답(Atlassian 와이어 snake_case). {@code accountId}=JWT/users.user_id 의 userId. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AtlassianUserInfo(
    @JsonProperty("account_id") String accountId, String email, String name, String picture) {}

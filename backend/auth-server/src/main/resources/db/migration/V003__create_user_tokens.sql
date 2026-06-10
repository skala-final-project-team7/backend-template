-- LINA Authorization Server — user_tokens 테이블 (Confluence OAuth 토큰 + cloudId, 1:1)
-- 앱 내 Confluence 페이지 미리보기(라이브 REST 호출)에 사용한다.
--   confluence_access/refresh_token_enc = AES-GCM 암호화 저장(평문 금지).
--       Atlassian access token 이 길어 여유있게 VARBINARY(2048) 로 잡는다.
--   cloud_id                = Confluence REST URL(/ex/confluence/{cloudId}/...) 구성용. 평문(민감 아님).
--   access_token_expires_at = access token 만료 시각. 만료 임박 시 refresh 로 갱신(rotating).
-- PK = user_key (사용자당 Confluence 토큰셋 1개, 1:1).
CREATE TABLE user_tokens (
    user_key                     BINARY(16)      NOT NULL,
    confluence_access_token_enc  VARBINARY(2048) NOT NULL,
    confluence_refresh_token_enc VARBINARY(2048) NOT NULL,
    cloud_id                     VARCHAR(64)     NOT NULL,
    access_token_expires_at      DATETIME        NOT NULL,
    created_at                   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_key),
    CONSTRAINT fk_user_tokens_user
        FOREIGN KEY (user_key) REFERENCES users (user_key) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

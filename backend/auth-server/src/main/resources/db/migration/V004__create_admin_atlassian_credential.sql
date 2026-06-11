-- LINA Authorization Server — admin_atlassian_credential 테이블
-- admin(role=ADMIN) 의 Atlassian 계정 credential. **Admin Key 수명주기 관리(activate/deactivate) 전용**.
--   site_url            = Admin Key 관리 API base URL. `POST/DELETE {site_url}/wiki/api/v2/admin-key`.
--                         (콘텐츠 조회 게이트웨이 `api.atlassian.com/ex/confluence/{cloudId}/...` 와 별개 — admin-key 관리는 site URL.)
--   admin_api_token_enc = Atlassian 계정 발급 API Token. AES-GCM 암호화 저장(평문 금지).
--                         Basic auth = base64(users.email : 복호화한 API Token). OAuth2 앱은 admin-key 리소스 접근 불가라 API Token 필수.
-- adminEmail 은 users.email 재사용(FK). 정적 credential — 만료/refresh 없음.
-- 콘텐츠 조회용 OAuth access/refresh + cloud_id 는 user_tokens(§V003) 에 별도 보관(혼동 금지).
CREATE TABLE admin_atlassian_credential (
    user_key            BINARY(16)      NOT NULL,
    site_url            VARCHAR(255)    NOT NULL,
    admin_api_token_enc VARBINARY(2048) NOT NULL,
    PRIMARY KEY (user_key),
    CONSTRAINT fk_admin_atl_cred_user
        FOREIGN KEY (user_key) REFERENCES users (user_key) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

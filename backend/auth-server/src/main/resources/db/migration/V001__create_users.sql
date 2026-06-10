-- LINA Authorization Server — users 테이블
-- 회원가입 완료 가정. LINA 발급 access token 을 저장한다(refresh token 은 이번 단계 미고려).
--   user_id              = Confluence accountId. 문서/JWT/RAG 의 `userId` = 이 값(이메일 아님!). RAG ACL(allowed_users)로 전달.
--   email                = 로그인 식별자(이메일). LINA access token 파싱 결과로 조회 키.
--   user_key             = PK. 애플리케이션이 UUID 생성 후 UUID_TO_BIN 으로 저장.
CREATE TABLE users (
    user_key             BINARY(16)            NOT NULL,
    user_id              VARCHAR(128)          NOT NULL,
    email                VARCHAR(255)          NOT NULL,
    name                 VARCHAR(128)          NULL,             -- 표시 이름(Confluence 응답에서 저장)
    profile_image_url    VARCHAR(512)          NULL,
    role                 ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    access_token         VARCHAR(512)          NOT NULL,
    last_login_at        DATETIME              NULL,             -- OAuth callback 시 갱신
    created_at           DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_key),
    UNIQUE KEY uk_users_user_id (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

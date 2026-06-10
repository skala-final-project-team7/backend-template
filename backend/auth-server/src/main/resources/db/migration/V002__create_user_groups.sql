-- LINA Authorization Server — user_groups 테이블 (사용자 group 멤버십, 1:N)
-- 로그인(OAuth callback) 시 Confluence memberof API 로 조회한 group 을 적재한다.
--   group_id = Confluence groupId (memberof 응답 results[].id). 행 단위 단일 값이며,
--              애플리케이션이 user_key 로 모아 `groups` 배열로 집계해 JWT claim·/ml/query
--              (Qdrant allowed_groups 매칭)에 전달한다. (DB 단수 group_id ↔ 와이어 복수 groups)
-- 복합 PK (user_key, group_id) 로 동일 group 중복을 막고 user_key 기준 조회를 인덱싱한다.
CREATE TABLE user_groups (
    user_key BINARY(16)   NOT NULL,
    group_id VARCHAR(128) NOT NULL,             -- 행 단위 단일 groupId (집계 시 `groups` 배열)
    PRIMARY KEY (user_key, group_id),
    CONSTRAINT fk_user_groups_user
        FOREIGN KEY (user_key) REFERENCES users (user_key) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

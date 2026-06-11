package com.lina.auth.token.repository;

import com.lina.auth.token.entity.UserGroup;
import com.lina.auth.token.entity.UserGroupId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** user_groups 영속. 로그인 시 기존 멤버십을 삭제 후 memberof 조회 결과로 교체 적재한다(docs/db-schema.md §6.3). */
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {

  /** 사용자의 group 멤버십 전체 조회 — JWT `groups` claim 배열 집계용. */
  List<UserGroup> findByUserKey(UUID userKey);

  /** 멤버십 교체(로그인 시)용 일괄 삭제. */
  long deleteByUserKey(UUID userKey);
}

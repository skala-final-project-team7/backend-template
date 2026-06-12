package com.lina.auth.token.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lina.auth.token.entity.UserGroup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserGroupRepositoryTest {

  @Autowired private UserGroupRepository userGroupRepository;
  @Autowired private TestEntityManager entityManager;

  private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_KEY = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private UserGroup membership(UUID userKey, String groupId) {
    return UserGroup.builder().userKey(userKey).groupId(groupId).build();
  }

  @Test
  @DisplayName("user_key 기준으로 본인의 group 멤버십만 조회된다")
  void shouldFindMembershipsByUserKey() {
    userGroupRepository.save(membership(USER_KEY, "group-a"));
    userGroupRepository.save(membership(USER_KEY, "group-b"));
    userGroupRepository.save(membership(OTHER_KEY, "group-c"));

    List<UserGroup> memberships = userGroupRepository.findByUserKey(USER_KEY);

    assertThat(memberships)
        .extracting(UserGroup::getGroupId)
        .containsExactlyInAnyOrder("group-a", "group-b");
  }

  @Test
  @DisplayName("동일 (user_key, group_id) 재저장 시 중복 행이 생기지 않는다(복합 PK)")
  void shouldNotDuplicateSameMembership() {
    userGroupRepository.saveAndFlush(membership(USER_KEY, "group-a"));
    userGroupRepository.saveAndFlush(membership(USER_KEY, "group-a"));

    assertThat(userGroupRepository.findByUserKey(USER_KEY)).hasSize(1);
  }

  @Test
  @DisplayName("로그인 시 멤버십 교체 — 기존 행 삭제 후 새 멤버십으로 재적재된다")
  void shouldReplaceMembershipsOnLogin() {
    userGroupRepository.save(membership(USER_KEY, "group-old-1"));
    userGroupRepository.save(membership(USER_KEY, "group-old-2"));
    entityManager.flush();

    userGroupRepository.deleteByUserKey(USER_KEY);
    userGroupRepository.save(membership(USER_KEY, "group-new"));
    entityManager.flush();
    entityManager.clear();

    assertThat(userGroupRepository.findByUserKey(USER_KEY))
        .extracting(UserGroup::getGroupId)
        .containsExactly("group-new");
  }
}

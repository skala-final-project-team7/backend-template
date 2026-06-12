package com.lina.auth.token.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** {@link UserGroup} 의 복합 PK(user_key, group_id) IdClass. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserGroupId implements Serializable {

  private UUID userKey;
  private String groupId;
}

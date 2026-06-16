package com.lina.bff.user.repository;

import java.util.Optional;

/** auth-server MySQL `users` read-only repository for BFF user profile APIs. */
public interface UserProfileReadRepository {

  Optional<UserProfileRow> findByUserId(String userId);
}

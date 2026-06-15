package com.lina.bff.admin.dashboard.repository;

import com.lina.bff.admin.dashboard.dto.AdminDashboardPageRequest;
import java.time.Instant;

/** auth-server MySQL users/user_groups 읽기 전용 repository 경계. */
public interface AdminUserReadRepository {

  AdminUserPage findUsers(AdminDashboardPageRequest pageRequest, Instant fromUtc, Instant toUtc);
}

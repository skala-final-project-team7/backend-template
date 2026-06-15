package com.lina.bff.admin.dashboard.repository;

import java.util.List;

/** 관리자 사용자 현황의 MySQL users 페이지 조회 결과. */
public record AdminUserPage(long totalUsers, long activeUsers, List<AdminUserRow> users) {

  public AdminUserPage {
    users = List.copyOf(users);
  }
}

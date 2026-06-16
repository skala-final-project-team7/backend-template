package com.lina.bff.user.controller;

import com.lina.bff.user.dto.UserMeResponse;
import com.lina.bff.user.service.UserMeService;
import com.lina.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserMeController {

  private final UserMeService userMeService;

  public UserMeController(UserMeService userMeService) {
    this.userMeService = userMeService;
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserMeResponse>> me() {
    return ResponseEntity.ok(ApiResponse.success(userMeService.getCurrentUser(), "사용자 정보 조회 성공"));
  }
}

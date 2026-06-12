package com.lina.bff.admin.controller;

import com.lina.bff.admin.dto.AdminIngestRequest;
import com.lina.bff.admin.dto.AdminIngestResponse;
import com.lina.bff.admin.service.AdminIngestService;
import com.lina.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminIngestController {

  private final AdminIngestService adminIngestService;

  @PostMapping("/ingest")
  public ApiResponse<AdminIngestResponse> startIngest(
      @Valid @RequestBody(required = false) AdminIngestRequest request) {
    return ApiResponse.success(adminIngestService.startIngest(request));
  }
}

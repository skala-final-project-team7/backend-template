package com.lina.bff.admin.controller;

import com.lina.bff.admin.dto.AdminIngestRequest;
import com.lina.bff.admin.dto.AdminIngestResponse;
import com.lina.bff.admin.dto.AdminIngestStatusResponse;
import com.lina.bff.admin.service.AdminIngestService;
import com.lina.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  public ResponseEntity<ApiResponse<AdminIngestResponse>> startIngest(
      @Valid @RequestBody(required = false) AdminIngestRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminIngestService.startIngest(request)));
  }

  /** 수집 진행상태 조회 — FE 폴링 대상(통합 이슈 #8). Data Ingestion status 를 중계한다. */
  @GetMapping("/ingest/status/{jobId}")
  public ResponseEntity<ApiResponse<AdminIngestStatusResponse>> ingestStatus(
      @PathVariable String jobId) {
    return ResponseEntity.ok(ApiResponse.success(adminIngestService.getIngestStatus(jobId)));
  }
}

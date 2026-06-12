package com.lina.bff.admin.dashboard.service;

import com.lina.bff.admin.dashboard.dto.AdminDashboardQuery;
import com.lina.bff.admin.dashboard.dto.AdminSyncResponse;
import com.lina.bff.admin.dashboard.dto.SyncHistoryItemResponse;
import com.lina.bff.admin.dashboard.repository.AdminSyncMongoRepository;
import com.lina.bff.admin.dashboard.support.AdminDashboardQueryParser;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 관리자 대시보드 동기화 이력 조회 서비스.
 * 작성일 : 2026-06-12
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-12, 4단계 Feature 7 — sync_logs 문서의 API 응답 매핑 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
@Service
public class AdminSyncService {

  private static final List<String> SYNC_ID_FIELDS =
      List.of("syncId", "sync_id", "jobId", "job_id");
  private static final List<String> UPDATED_PAGE_FIELDS =
      List.of("updatedPages", "updated_pages", "processedPages", "processed_pages");
  private static final List<String> DELETED_PAGE_FIELDS = List.of("deletedPages", "deleted_pages");
  private static final List<String> DURATION_SECOND_FIELDS =
      List.of("duration", "durationSeconds", "duration_seconds");
  private static final List<String> DURATION_MILLIS_FIELDS =
      List.of("durationMillis", "duration_millis", "durationMs", "duration_ms");
  private static final List<String> STARTED_TIME_FIELDS =
      List.of("startedAt", "started_at", "createdAt", "created_at");
  private static final List<String> COMPLETED_TIME_FIELDS =
      List.of(
          "completedAt", "completed_at", "finishedAt", "finished_at", "updatedAt", "updated_at");

  private final AdminSyncMongoRepository adminSyncMongoRepository;

  public AdminSyncService(AdminSyncMongoRepository adminSyncMongoRepository) {
    this.adminSyncMongoRepository = adminSyncMongoRepository;
  }

  public AdminSyncResponse getSyncHistory(AdminDashboardQuery query) {
    List<SyncHistoryItemResponse> items =
        adminSyncMongoRepository
            .findSyncLogsBetween(
                query.timeRange().fromUtc(), query.timeRange().toUtc(), query.pageRequest())
            .stream()
            .map(this::toResponse)
            .toList();
    return new AdminSyncResponse(items);
  }

  private SyncHistoryItemResponse toResponse(Document document) {
    Instant completedAt = firstInstant(document, COMPLETED_TIME_FIELDS);
    return new SyncHistoryItemResponse(
        firstString(document, SYNC_ID_FIELDS, objectId(document)),
        normalizeStatus(firstString(document, List.of("status"), "UNKNOWN")),
        firstLong(document, UPDATED_PAGE_FIELDS),
        firstLong(document, DELETED_PAGE_FIELDS),
        durationSeconds(document, completedAt),
        toKst(completedAt));
  }

  private long durationSeconds(Document document, Instant completedAt) {
    Long explicitSeconds = firstLongOrNull(document, DURATION_SECOND_FIELDS);
    if (explicitSeconds != null) {
      return Math.max(0L, explicitSeconds);
    }

    Long millis = firstLongOrNull(document, DURATION_MILLIS_FIELDS);
    if (millis != null) {
      return Math.max(0L, Math.round(millis / 1000.0));
    }

    Instant startedAt = firstInstant(document, STARTED_TIME_FIELDS);
    if (startedAt == null || completedAt == null) {
      return 0L;
    }
    return Math.max(0L, Duration.between(startedAt, completedAt).toSeconds());
  }

  private String normalizeStatus(String status) {
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "SUCCESS", "SUCCEEDED", "DONE" -> "COMPLETED";
      case "ERROR" -> "FAILED";
      case "RUNNING" -> "IN_PROGRESS";
      default -> normalized;
    };
  }

  private static String firstString(Document document, List<String> fieldNames, String fallback) {
    for (String fieldName : fieldNames) {
      Object value = document.get(fieldName);
      if (value != null && !value.toString().isBlank()) {
        return value.toString();
      }
    }
    return fallback;
  }

  private static long firstLong(Document document, List<String> fieldNames) {
    Long value = firstLongOrNull(document, fieldNames);
    return value == null ? 0L : value;
  }

  private static Long firstLongOrNull(Document document, List<String> fieldNames) {
    for (String fieldName : fieldNames) {
      Long value = toLong(document.get(fieldName));
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static Instant firstInstant(Document document, List<String> fieldNames) {
    for (String fieldName : fieldNames) {
      Instant instant = toInstant(document.get(fieldName));
      if (instant != null) {
        return instant;
      }
    }
    return null;
  }

  private static Long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return null;
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof Date date) {
      return date.toInstant();
    }
    if (value instanceof String text && !text.isBlank()) {
      return ZonedDateTime.parse(text).toInstant();
    }
    return null;
  }

  private static ZonedDateTime toKst(Instant instant) {
    return instant == null ? null : instant.atZone(AdminDashboardQueryParser.KST);
  }

  private static String objectId(Document document) {
    Object id = document.get("_id");
    if (id instanceof ObjectId objectId) {
      return objectId.toHexString();
    }
    return id == null ? "" : id.toString();
  }
}

package com.lina.bff.feedback.repository;

import com.lina.bff.feedback.entity.Feedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 피드백 영속 접근. 메시지당 1건(uniq_feedbacks_message)이므로 message_id 로 단건 조회한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — 메시지별 단건 조회 정의
 * --------------------------------------------------
 * [호환성]
 *   - Spring Data JPA 3.3.x / MySQL 8.x
 * --------------------------------------------------
 * </pre>
 */
public interface FeedbackRepository extends JpaRepository<Feedback, String> {

  /**
   * 메시지에 등록된 피드백을 조회한다(upsert 분기용).
   *
   * <ul>
   *   <li>사용 인덱스: uniq_feedbacks_message (message_id) UNIQUE
   *   <li>필터: message_id 일치
   *   <li>호출 위치: FeedbackService 등록/갱신 (Feature 6)
   * </ul>
   *
   * @param messageId 대상 메시지 식별자
   * @return 기존 피드백 (없으면 Optional.empty → 신규 등록)
   */
  Optional<Feedback> findByMessageId(String messageId);
}

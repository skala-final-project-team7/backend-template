package com.lina.bff.chat.repository;

import com.lina.bff.chat.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 메시지 영속 접근. 멀티턴 복원을 위해 대화별 활성 메시지를 시간순으로 조회한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — 대화별 이력 조회 정의
 * --------------------------------------------------
 * [호환성]
 *   - Spring Data JPA 3.3.x / MySQL 8.x
 * --------------------------------------------------
 * </pre>
 */
public interface MessageRepository extends JpaRepository<Message, String> {

  /**
   * 대화방의 활성 메시지 이력을 생성 시간 오름차순으로 조회한다(멀티턴 복원).
   *
   * <ul>
   *   <li>사용 인덱스: idx_messages_conversation_created (conversation_id, deleted_at, created_at)
   *   <li>필터: conversation_id 일치 + deleted_at IS NULL
   *   <li>정렬: created_at ASC (질문→답변 순서 보존)
   *   <li>호출 위치: 메시지 이력 조회/멀티턴 history 구성 (Feature 4·5)
   * </ul>
   *
   * @param conversationId 대화 식별자
   * @return 시간순 활성 메시지 목록 (없으면 빈 리스트)
   */
  List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(String conversationId);
}

package com.lina.bff.chat.repository;

import com.lina.bff.chat.entity.Conversation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 대화방 영속 접근. 모든 조회는 soft delete(deleted_at IS NULL) 활성 행만 대상으로 한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — 목록/단건 조회 정의
 * --------------------------------------------------
 * [호환성]
 *   - Spring Data JPA 3.3.x / MySQL 8.x
 * --------------------------------------------------
 * </pre>
 */
public interface ConversationRepository extends JpaRepository<Conversation, String> {

  /**
   * 사용자별 활성 대화 목록을 최신 메시지 순으로 페이징 조회한다.
   *
   * <ul>
   *   <li>사용 인덱스: idx_conversations_user_active_recent (user_id, deleted_at, last_message_at)
   *   <li>필터: user_id 일치 + deleted_at IS NULL (soft delete 제외)
   *   <li>정렬: last_message_at DESC
   *   <li>호출 위치: ConversationService.selectConversationList (Feature 3)
   * </ul>
   *
   * @param userId 조회 대상 사용자 식별자
   * @param pageable page/size 페이징 정보
   * @return 활성 대화 페이지 (없으면 빈 페이지)
   */
  Page<Conversation> findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(
      String userId, Pageable pageable);

  /**
   * 활성 대화 단건을 조회한다. 존재하지 않거나 soft delete 된 경우 빈 Optional.
   *
   * <ul>
   *   <li>사용 인덱스: PRIMARY (conversation_id)
   *   <li>필터: conversation_id 일치 + deleted_at IS NULL
   *   <li>호출 위치: 대화 단건/메시지 이력/제목 수정/삭제 (Feature 3·4)
   * </ul>
   *
   * @param conversationId 대화 식별자
   * @return 활성 대화 (없으면 Optional.empty)
   */
  Optional<Conversation> findByConversationIdAndDeletedAtIsNull(String conversationId);
}

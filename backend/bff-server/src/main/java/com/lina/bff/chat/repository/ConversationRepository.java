package com.lina.bff.chat.repository;

import com.lina.bff.chat.entity.Conversation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 대화방(conversations) 컬렉션 영속 접근. 모든 조회는 soft delete(deletedAt == null) 활성 문서만 대상으로 한다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — 목록/단건 조회 정의
 *   - 2026-05-20, 데이터 저장소 변경, JpaRepository → MongoRepository
 * --------------------------------------------------
 * [호환성]
 *   - Spring Data MongoDB 4.x / MongoDB 7.x
 * --------------------------------------------------
 * </pre>
 */
public interface ConversationRepository extends MongoRepository<Conversation, String> {

  /**
   * 사용자별 활성 대화 목록을 lastMessageAt 내림차순으로 페이징 조회한다.
   *
   * <ul>
   *   <li>사용 인덱스: idx_conversations_user_active_recent {userId:1, deletedAt:1, lastMessageAt:-1}
   *   <li>필터: userId 일치 + deletedAt == null (soft delete 제외)
   *   <li>정렬: lastMessageAt DESC
   *   <li>호출 위치: ConversationService.selectConversationList (Feature 3)
   * </ul>
   *
   * @param userId 조회 대상 사용자 식별자
   * @param pageable page/size 페이징 정보
   * @return 활성 대화 페이지 (없으면 빈 페이지)
   */
  Page<Conversation> findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(
      String userId, Pageable pageable);

  /**
   * 활성 대화 단건을 조회한다. 존재하지 않거나 soft delete 된 경우 빈 Optional.
   *
   * <ul>
   *   <li>사용 인덱스: PRIMARY (_id)
   *   <li>필터: _id 일치 + deletedAt == null
   *   <li>호출 위치: 대화 단건/메시지 이력/제목 수정/삭제 (Feature 3·4)
   * </ul>
   *
   * @param conversationId 대화 식별자
   * @return 활성 대화 (없으면 Optional.empty)
   */
  Optional<Conversation> findByConversationIdAndDeletedAtIsNull(String conversationId);
}

package com.lina.bff.chat.repository;

import com.lina.bff.chat.entity.Message;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 메시지(messages) 컬렉션 영속 접근. 멀티턴 복원을 위해 대화별 활성 메시지를 시간순으로 조회한다.
 *           인용 출처는 별도 컬렉션이 아니라 Message.sources 내장 배열로 함께 적재/조회된다.
 * 작성일 : 2026-05-19
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-19, 최초 작성, 2단계 Feature 1 — 대화별 이력 조회 정의
 *   - 2026-05-20, 데이터 저장소 변경, JpaRepository → MongoRepository
 * --------------------------------------------------
 * [호환성]
 *   - Spring Data MongoDB 4.x / MongoDB 7.x
 * --------------------------------------------------
 * </pre>
 */
public interface MessageRepository extends MongoRepository<Message, String> {

  /**
   * 대화방의 활성 메시지 이력을 생성 시간 오름차순으로 조회한다(멀티턴 복원).
   *
   * <ul>
   *   <li>사용 인덱스: idx_messages_conversation_active_created {conversationId:1, deletedAt:1,
   *       createdAt:1}
   *   <li>필터: conversationId 일치 + deletedAt == null
   *   <li>정렬: createdAt ASC (질문→답변 순서 보존)
   *   <li>호출 위치: 메시지 이력 조회/멀티턴 history 구성 (Feature 4·5)
   * </ul>
   *
   * @param conversationId 대화 식별자
   * @return 시간순 활성 메시지 목록 (없으면 빈 리스트)
   */
  List<Message> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(String conversationId);

  /**
   * 활성 메시지 존재 여부를 확인한다(피드백 등록 시 대상 메시지 검증).
   *
   * <ul>
   *   <li>필터: messageId 일치 + deletedAt == null
   *   <li>호출 위치: FeedbackService 피드백 등록/갱신 (Feature 6) — 없는/삭제 메시지 404 판정
   * </ul>
   *
   * @param messageId 대상 메시지 식별자
   * @return 활성 메시지가 존재하면 true
   */
  boolean existsByMessageIdAndDeletedAtIsNull(String messageId);
}

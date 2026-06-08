package com.lina.bff.rag.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : BFF 가 RAG Pipeline POST /ml/query 로 전달하는 입력 DTO.
 *           question/userId/groups/conversationId/history/stream 을 camelCase request body 로
 *           직렬화한다. /ml/query 는 Confluence credential 을 받지 않으므로 accessToken/cloudId 필드는
 *           의도적으로 포함하지 않는다.
 * 작성일 : 2026-06-05
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-05, 최초 작성, 2단계 Feature 5 RAG 질의 요청 경계 추가
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x / Jackson 2.17.x 기준
 * --------------------------------------------------
 * </pre>
 */
public record RagQueryCommand(
    @NotBlank String question,
    @NotBlank String userId,
    @NotEmpty List<String> groups,
    String conversationId,
    List<HistoryMessage> history,
    boolean stream) {

  /**
   * /ml/query 요청 본문을 생성한다.
   *
   * @param question 사용자 자연어 질문
   * @param userId ACL pre-filtering 에 사용할 사용자 식별자
   * @param groups ACL pre-filtering 에 사용할 사용자 그룹 목록. 빈 배열은 ChatService 의 fail-closed 게이트에서 차단된다.
   * @param conversationId 대화 컨텍스트 식별자
   * @param history RAG 에 전달할 최근 대화 이력. role 값은 저장값 그대로 user/assistant lowercase 를 사용한다.
   * @param stream BFF 는 SSE 스트리밍을 위해 항상 true 로 전달한다.
   */
  public RagQueryCommand {
    groups = groups == null ? List.of() : List.copyOf(groups);
    history = history == null ? List.of() : List.copyOf(history);
  }

  /**
   * /ml/query history[] 항목이다.
   *
   * @param role 메시지 역할. user 또는 assistant lowercase
   * @param content 이전 발화 본문
   */
  public record HistoryMessage(@NotBlank String role, @NotBlank String content) {}
}

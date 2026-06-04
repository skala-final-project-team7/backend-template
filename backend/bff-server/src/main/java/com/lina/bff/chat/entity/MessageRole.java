package com.lina.bff.chat.entity;

/**
 * 메시지 작성 주체. 사용자 질문(user)과 AI 답변(assistant)을 구분한다.
 *
 * <p>상수 이름이 lowercase 인 이유: LLM/OpenAI 산업 표준의 chat message role 표기(`user`/`assistant`)를 저장·와이어 양쪽에
 * 동일하게 사용하기 위함이다. {@code Enum.name()} 이 그대로 MongoDB 저장값과 JSON 직렬화 값이 되므로 boundary 변환 없이 RAG {@code
 * /ml/query} {@code history[].role} 과 일치한다. (Common Enum 표기 정책의 명시된 예외 — {@code docs/api-spec.md}.)
 */
public enum MessageRole {
  user,
  assistant
}

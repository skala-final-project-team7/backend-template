package com.lina.bff.chat.entity;

/** RAG 답변 신뢰도 검증 결과. assistant 메시지에만 존재할 수 있다(nullable). */
public enum VerificationResult {
  SUPPORTED,
  PARTIALLY_SUPPORTED,
  NOT_SUPPORTED
}

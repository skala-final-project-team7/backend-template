package com.lina.bff.feedback.entity;

/** 답변에 대한 사용자 피드백 평가. API 응답·DB 저장 모두 UPPER 표기(`LIKE`/`DISLIKE`)로 통일된다(`docs/api-spec.md` §1-3). */
public enum FeedbackRating {
  LIKE,
  DISLIKE
}

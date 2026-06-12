package com.lina.bff.config;

import java.util.List;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : 현재 요청 사용자 정보(userId/groups/spaceKey) 공급 인터페이스.
 *           2단계(중간발표)에서는 고정 데모 구현체({@link FixedDemoUserProvider})를 사용하고,
 *           3단계 이후 JWT Claim 기반 구현체로 교체해 Controller/Service 변경 없이 인증 흐름을 전환한다.
 * 작성일 : 2026-05-21
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-05-21, 최초 작성, 2단계 Feature 2 — 인증 부재 격리용 추상화 경계 도입
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS
 *   - Spring Boot 3.3.x
 * --------------------------------------------------
 * </pre>
 */
public interface CurrentUserProvider {

  /** 현재 요청 사용자 식별자. 2단계에서는 고정 데모 사용자 ID. */
  String getUserId();

  /** 현재 요청 사용자가 속한 그룹 목록. ACL 필터 조건으로 RAG Pipeline 호출에 전달된다. */
  List<String> getGroups();

  /** RAG Pipeline 검색 컨텍스트로 사용되는 Confluence Space Key. 2단계에서는 고정 데모 스페이스. */
  String getSpaceKey();

  /** 현재 요청 사용자의 애플리케이션 역할. 관리자 API 권한 경계에서 사용한다. */
  String getRole();
}

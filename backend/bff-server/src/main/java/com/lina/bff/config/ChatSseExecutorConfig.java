package com.lina.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 *
 *
 * <pre>
 * --------------------------------------------------
 * 작성자 : LINA Backend Team
 * 작성목적 : FE-BFF SSE 응답에 RAG 스트림을 중계하는 백그라운드 작업 실행기 정의.
 *           요청별 중계 작업은 Spring 이 관리하는 Virtual Thread 기반 TaskExecutor 에 제출한다.
 * 작성일 : 2026-06-07
 * 변경사항 내역 (날짜, 변경목적, 변경내용 순)
 *   - 2026-06-07, 최초 작성, 직접 Thread.ofVirtual 생성 대신 TaskExecutor 빈 도입
 * --------------------------------------------------
 * [호환성]
 *   - JDK 21 LTS — SimpleAsyncTaskExecutor virtual threads 사용
 *   - Spring Boot 3.3.x / Spring Core 6.1.x 기준
 * --------------------------------------------------
 * </pre>
 */
@Configuration
public class ChatSseExecutorConfig {

  @Bean
  TaskExecutor chatSseTaskExecutor() {
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("chat-sse-");
    executor.setVirtualThreads(true);
    return executor;
  }
}

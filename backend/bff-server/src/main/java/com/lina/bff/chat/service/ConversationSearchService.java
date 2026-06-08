package com.lina.bff.chat.service;

import com.lina.bff.chat.dto.ConversationSearchResponse;
import com.lina.bff.chat.dto.ConversationSearchResultResponse;
import com.lina.bff.chat.dto.MatchedMessageResponse;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.chat.support.SearchTextSupport;
import com.lina.bff.chat.support.SearchTextSupport.Snippet;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 검색(Feature 7) 서비스. 본인 활성 대화의 메시지 본문에서 검색어를 매칭하고 대화 단위로 묶어 반환한다.
 *
 * <p>권한 격리: {@link CurrentUserProvider} 가 제공하는 현재 사용자 식별자로 검색 범위를 본인 활성 대화로 제한한다(타 사용자 대화 노출 차단).
 * soft delete 된 대화·메시지는 모두 제외한다.
 */
@Service
public class ConversationSearchService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int MIN_QUERY_LENGTH = 2;
  private static final int MAX_QUERY_LENGTH = 50;
  private static final int MAX_PAGE_SIZE = 50;
  private static final int MAX_MATCHED_SAMPLE = 3;

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final CurrentUserProvider currentUserProvider;

  public ConversationSearchService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      CurrentUserProvider currentUserProvider) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.currentUserProvider = currentUserProvider;
  }

  @Transactional(readOnly = true)
  public ConversationSearchResponse search(String q, int page, int size) {
    String term = q == null ? "" : q.strip();
    if (term.length() < MIN_QUERY_LENGTH || term.length() > MAX_QUERY_LENGTH) {
      throw new BizException(
          ErrorCode.INVALID_SEARCH_QUERY,
          "검색어는 공백을 제외하고 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자여야 합니다.");
    }
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new BizException(
          ErrorCode.INVALID_SEARCH_QUERY, "page 는 0 이상, size 는 1~" + MAX_PAGE_SIZE + " 이어야 합니다.");
    }

    String userId = currentUserProvider.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "현재 사용자 식별자가 없습니다.");
    }

    List<Conversation> conversations =
        conversationRepository.findByUserIdAndDeletedAtIsNull(userId);
    if (conversations.isEmpty()) {
      return new ConversationSearchResponse(List.of(), 0, page, size);
    }

    Map<String, Conversation> conversationsById = new LinkedHashMap<>();
    for (Conversation conversation : conversations) {
      conversationsById.put(conversation.getConversationId(), conversation);
    }

    List<Message> matched =
        messageRepository.searchActiveByConversationIdsAndContent(
            List.copyOf(conversationsById.keySet()), SearchTextSupport.escapeRegex(term));

    Map<String, List<Message>> matchesByConversation = new LinkedHashMap<>();
    for (Message message : matched) {
      matchesByConversation
          .computeIfAbsent(message.getConversationId(), key -> new ArrayList<>())
          .add(message);
    }

    List<ConversationSearchResultResponse> results =
        matchesByConversation.entrySet().stream()
            .sorted(
                Comparator.comparing(
                        (Map.Entry<String, List<Message>> entry) ->
                            conversationsById.get(entry.getKey()).getLastMessageAt())
                    .reversed())
            .map(entry -> toResult(conversationsById.get(entry.getKey()), entry.getValue(), term))
            .toList();

    int fromIndex = page * size;
    List<ConversationSearchResultResponse> pageResults =
        fromIndex >= results.size()
            ? List.of()
            : List.copyOf(results.subList(fromIndex, Math.min(fromIndex + size, results.size())));
    return new ConversationSearchResponse(pageResults, results.size(), page, size);
  }

  private ConversationSearchResultResponse toResult(
      Conversation conversation, List<Message> matches, String term) {
    List<MatchedMessageResponse> sample =
        matches.stream()
            .limit(MAX_MATCHED_SAMPLE)
            .map(message -> toMatched(message, term))
            .toList();
    return new ConversationSearchResultResponse(
        conversation.getConversationId(),
        conversation.getTitle(),
        toKst(conversation.getLastMessageAt()),
        conversation.isPinned(),
        sample,
        matches.size());
  }

  private MatchedMessageResponse toMatched(Message message, String term) {
    Snippet snippet = SearchTextSupport.extract(message.getContent(), term);
    return new MatchedMessageResponse(
        message.getMessageId(),
        message.getRole(),
        snippet.snippet(),
        snippet.matchPositions(),
        toKst(message.getCreatedAt()));
  }

  private ZonedDateTime toKst(Instant instant) {
    return instant.atZone(KST);
  }
}

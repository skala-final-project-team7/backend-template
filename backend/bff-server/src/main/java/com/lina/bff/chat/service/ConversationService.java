package com.lina.bff.chat.service;

import com.lina.bff.chat.dto.ConversationListResponse;
import com.lina.bff.chat.dto.ConversationSummaryResponse;
import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.dto.UpdateConversationRequest;
import com.lina.bff.chat.dto.UpdateConversationResponse;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.config.CurrentUserProvider;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private static final String DEFAULT_TITLE = "새 대화";
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final ConversationRepository conversationRepository;
  private final CurrentUserProvider currentUserProvider;

  public ConversationService(
      ConversationRepository conversationRepository, CurrentUserProvider currentUserProvider) {
    this.conversationRepository = conversationRepository;
    this.currentUserProvider = currentUserProvider;
  }

  @Transactional
  public CreateConversationResponse createConversation() {
    String userId = currentUserProvider.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "현재 사용자 식별자가 없습니다.");
    }
    Conversation conversation = Conversation.builder().userId(userId).title(DEFAULT_TITLE).build();
    Conversation saved = conversationRepository.save(conversation);
    return new CreateConversationResponse(
        saved.getConversationId(), saved.getTitle(), saved.isPinned(), toKst(saved.getCreatedAt()));
  }

  @Transactional(readOnly = true)
  public ConversationListResponse listConversations(int page, int size) {
    String userId = currentUserProvider.getUserId();
    if (userId == null || userId.isBlank()) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "현재 사용자 식별자가 없습니다.");
    }
    PageRequest pageRequest = PageRequest.of(page, size);
    Page<Conversation> conversations =
        conversationRepository.findByUserIdAndDeletedAtIsNullOrderByIsPinnedDescLastMessageAtDesc(
            userId, pageRequest);
    List<ConversationSummaryResponse> summaries =
        conversations.stream()
            .map(
                conversation ->
                    new ConversationSummaryResponse(
                        conversation.getConversationId(),
                        conversation.getTitle(),
                        toKst(conversation.getLastMessageAt()),
                        conversation.isPinned()))
            .toList();
    return new ConversationListResponse(summaries, conversations.getTotalElements(), page, size);
  }

  @Transactional
  public UpdateConversationResponse updateConversation(
      String conversationId, UpdateConversationRequest request) {
    if (request.title() == null && request.isPinned() == null) {
      throw new BizException(ErrorCode.INVALID_REQUEST, "title 또는 isPinned 중 하나는 필요합니다.");
    }
    Conversation conversation =
        conversationRepository
            .findByConversationIdAndDeletedAtIsNull(conversationId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));
    conversation.update(request.title(), request.isPinned());
    Conversation saved = conversationRepository.save(conversation);
    return new UpdateConversationResponse(
        saved.getConversationId(), saved.getTitle(), saved.isPinned(), toKst(saved.getUpdatedAt()));
  }

  private ZonedDateTime toKst(java.time.Instant instant) {
    return instant.atZone(KST);
  }
}

package com.lina.bff.chat.service;

import com.lina.bff.chat.dto.ConversationListResponse;
import com.lina.bff.chat.dto.ConversationSummaryResponse;
import com.lina.bff.chat.dto.CreateConversationResponse;
import com.lina.bff.chat.dto.MessageHistoryResponse;
import com.lina.bff.chat.dto.MessageResponse;
import com.lina.bff.chat.dto.SourceResponse;
import com.lina.bff.chat.dto.UpdateConversationRequest;
import com.lina.bff.chat.dto.UpdateConversationResponse;
import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
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
  private final MessageRepository messageRepository;
  private final CurrentUserProvider currentUserProvider;

  public ConversationService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      CurrentUserProvider currentUserProvider) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
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

  @Transactional
  public void deleteConversation(String conversationId) {
    Conversation conversation =
        conversationRepository
            .findByConversationIdAndDeletedAtIsNull(conversationId)
            .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));
    conversation.markDeleted();
    conversationRepository.save(conversation);
  }

  @Transactional(readOnly = true)
  public MessageHistoryResponse getMessageHistory(String conversationId) {
    ensureConversationExists(conversationId);
    return new MessageHistoryResponse(
        conversationId,
        messageRepository
            .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId)
            .stream()
            .map(this::toMessageResponse)
            .toList());
  }

  private void ensureConversationExists(String conversationId) {
    conversationRepository
        .findByConversationIdAndDeletedAtIsNull(conversationId)
        .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));
  }

  private MessageResponse toMessageResponse(Message message) {
    return new MessageResponse(
        message.getMessageId(),
        message.getRole(),
        message.getContent(),
        message.getSources().stream().map(this::toSourceResponse).toList(),
        message.getConfidenceScore(),
        message.getVerificationResult(),
        toKst(message.getCreatedAt()));
  }

  private SourceResponse toSourceResponse(MessageSource source) {
    return new SourceResponse(
        source.getTitle(),
        source.getPageId(),
        source.getSpaceId(),
        source.getSpaceName(),
        source.getUrl(),
        toKst(source.getSourceUpdatedAt()),
        source.getRelevanceScore());
  }

  private ZonedDateTime toKst(java.time.Instant instant) {
    return instant.atZone(KST);
  }
}

package com.lina.bff.chat.service;

import com.lina.bff.chat.entity.Conversation;
import com.lina.bff.chat.entity.Message;
import com.lina.bff.chat.entity.MessageRole;
import com.lina.bff.chat.entity.MessageSource;
import com.lina.bff.chat.entity.VerificationResult;
import com.lina.bff.chat.repository.ConversationRepository;
import com.lina.bff.chat.repository.MessageRepository;
import com.lina.bff.rag.client.dto.RagQueryCommand;
import com.lina.common.exception.BizException;
import com.lina.common.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatMessagePersistenceService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;

  public ChatMessagePersistenceService(
      ConversationRepository conversationRepository, MessageRepository messageRepository) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
  }

  public Conversation loadConversation(String conversationId) {
    return conversationRepository
        .findByConversationIdAndDeletedAtIsNull(conversationId)
        .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "해당 대화를 찾을 수 없습니다."));
  }

  public List<RagQueryCommand.HistoryMessage> recentHistory(
      String conversationId, int historyTurns) {
    List<Message> messages =
        messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(conversationId);
    int fromIndex = Math.max(messages.size() - historyTurns, 0);
    return messages.subList(fromIndex, messages.size()).stream()
        .map(
            message ->
                new RagQueryCommand.HistoryMessage(message.getRole().name(), message.getContent()))
        .toList();
  }

  public Message saveUserMessage(Conversation conversation, String question) {
    Message userMessage =
        Message.builder()
            .conversationId(conversation.getConversationId())
            .role(MessageRole.user)
            .content(question)
            .build();
    Message saved = messageRepository.save(userMessage);
    conversation.recordMessageAt(saved.getCreatedAt());
    conversationRepository.save(conversation);
    return saved;
  }

  public Message saveAssistantMessage(
      Conversation conversation,
      String content,
      List<MessageSource> sources,
      Double confidenceScore,
      VerificationResult verificationResult) {
    Message message =
        Message.builder()
            .conversationId(conversation.getConversationId())
            .role(MessageRole.assistant)
            .content(content)
            .sources(sources)
            .confidenceScore(confidenceScore)
            .verificationResult(verificationResult)
            .build();
    Message saved = messageRepository.save(message);
    conversation.recordMessageAt(saved.getCreatedAt());
    conversationRepository.save(conversation);
    return saved;
  }
}

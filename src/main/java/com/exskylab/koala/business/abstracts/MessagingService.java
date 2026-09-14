package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MessagingService {

    Conversation joinJobCrew(UUID jobId, UUID workerId);

    void leaveJobCrew(UUID jobId, UUID workerId);

    void joinCompanyCrews(UUID companyId, UUID userId);

    void leaveCompanyCrews(UUID companyId, UUID userId);

    Conversation openConversation(UUID jobId, UUID workerId);

    Message sendMessage(UUID conversationId, String body);

    Page<Message> getMessages(UUID conversationId, Pageable pageable);

    com.exskylab.koala.core.dtos.common.PageDto<com.exskylab.koala.core.dtos.messaging.response.ConversationDto> getMyConversations(Pageable pageable);

    int markRead(UUID conversationId);

    UserBlock block(UUID blockedUserId, String reason);

    void unblock(UUID blockedUserId);

    List<UserBlock> getMyBlocks();

    Map<UUID, Long> unreadCounts(Collection<UUID> conversationIds);

    void assertParticipant(UUID conversationId, UUID userId);

    Message getMessageForReport(UUID messageId, UUID reporterId);
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageDao extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = {"sender", "sender.profilePicture"})
    Page<Message> findAllByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    @Modifying
    @Query("""
            update Message m set m.readAt = :readAt
            where m.conversation.id = :conversationId
              and m.sender.id <> :readerId
              and m.readAt is null
            """)
    int markConversationRead(@Param("conversationId") UUID conversationId,
                             @Param("readerId") UUID readerId,
                             @Param("readAt") Instant readAt);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID readerId);

    @Query("""
            select m.conversation.id, count(m) from Message m
            where m.conversation.id in :conversationIds
              and m.sender.id <> :readerId
              and m.readAt is null
            group by m.conversation.id
            """)
    List<Object[]> countUnreadByConversationIds(@Param("conversationIds") Collection<UUID> conversationIds,
                                               @Param("readerId") UUID readerId);
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.ConversationKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationDao extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByJobIdAndWorkerIdAndKind(UUID jobId, UUID workerId, ConversationKind kind);

    @EntityGraph(attributePaths = {"job", "employer", "employer.profilePicture", "worker", "worker.profilePicture",
            "participants", "participants.user"})
    Optional<Conversation> findWithParticipantsById(UUID id);

    @EntityGraph(attributePaths = {"job", "employer", "employer.profilePicture", "worker", "worker.profilePicture"})
    @Query("""
            select c from Conversation c
            where exists (select 1 from ConversationParticipant p
                          where p.conversation = c and p.user.id = :userId)
            """)
    Page<Conversation> findAllForParticipant(@Param("userId") UUID userId, Pageable pageable);

    Optional<Conversation> findByJobIdAndKind(UUID jobId, ConversationKind kind);

    @Query("""
            select count(c) > 0 from Conversation c
            where exists (select 1 from ConversationParticipant a where a.conversation = c and a.user.id = :first)
              and exists (select 1 from ConversationParticipant b where b.conversation = c and b.user.id = :second)
            """)
    boolean existsBetweenParticipants(@Param("first") UUID first, @Param("second") UUID second);
}

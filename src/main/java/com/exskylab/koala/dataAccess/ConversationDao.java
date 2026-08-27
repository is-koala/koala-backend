package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Conversation;
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

    Optional<Conversation> findByJobIdAndWorkerId(UUID jobId, UUID workerId);

    @EntityGraph(attributePaths = {"job", "employer", "worker"})
    Optional<Conversation> findWithParticipantsById(UUID id);

    @EntityGraph(attributePaths = {"job", "employer", "worker"})
    @Query("select c from Conversation c where c.employer.id = :userId or c.worker.id = :userId")
    Page<Conversation> findAllForParticipant(@Param("userId") UUID userId, Pageable pageable);
}

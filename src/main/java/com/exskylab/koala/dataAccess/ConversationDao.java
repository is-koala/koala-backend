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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    @EntityGraph(attributePaths = {"job", "participants"})
    List<Conversation> findAllByJobCompanyIdAndKind(UUID companyId, ConversationKind kind);

    @Query("""
            select count(c) > 0 from Conversation c
            where exists (select 1 from ConversationParticipant a where a.conversation = c and a.user.id = :first)
              and exists (select 1 from ConversationParticipant b where b.conversation = c and b.user.id = :second)
            """)
    boolean existsBetweenParticipants(@Param("first") UUID first, @Param("second") UUID second);

    @Query(value = """
            select distinct on (p.conversation_id, p.user_id)
                   p.conversation_id as "conversationId",
                   p.user_id as "userId",
                   u.first_name as "firstName",
                   u.last_name as "lastName",
                   picture.file_url as "profilePictureFileUrl",
                   case when p.user_id = c.employer_id then 'EMPLOYER'
                        when c.kind = 'DIRECT' then 'WORKER'
                        when exists (select 1 from job_assignments a
                                      where a.job_id = c.job_id and a.worker_id = p.user_id
                                        and a.is_deleted = false) then 'WORKER'
                        else 'COMPANY_CONTACT' end as "role",
                   p.joined_at as "joinedAt",
                   p.last_read_at as "lastReadAt",
                   p.is_deleted as "leftConversation"
            from conversation_participants p
            join conversations c on c.id = p.conversation_id
            join users u on u.id = p.user_id
            left join media picture on picture.id = u.profile_picture_id and picture.is_deleted = false
            where p.conversation_id in (:conversationIds)
            order by p.conversation_id, p.user_id, p.is_deleted asc, p.joined_at desc
            """, nativeQuery = true)
    List<ParticipantView> findParticipantRows(@Param("conversationIds") Collection<UUID> conversationIds);

    interface ParticipantView {
        UUID getConversationId();

        UUID getUserId();

        String getFirstName();

        String getLastName();

        String getProfilePictureFileUrl();

        String getRole();

        Instant getJoinedAt();

        Instant getLastReadAt();

        Boolean getLeftConversation();
    }
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface IysConsentTaskDao extends JpaRepository<IysConsentTask, UUID> {

    @EntityGraph(attributePaths = {"consent"})
    java.util.Optional<IysConsentTask> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"user", "consent"})
    Optional<IysConsentTask> findTopByUserIdAndChannelOrderByDecisionSequenceDesc(UUID userId,
                                                                                  NotificationChannel channel);

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:userId as text) || ':' || :channel, 0))",
            nativeQuery = true)
    void lockDecisionStream(@Param("userId") UUID userId, @Param("channel") String channel);

    @Query("""
            select task.id from IysConsentTask task
            where task.status = :status and task.nextAttemptAt <= :dueBefore
            order by task.nextAttemptAt asc
            """)
    List<UUID> findDueIds(@Param("status") IysConsentTaskStatus status,
                          @Param("dueBefore") Instant dueBefore, Pageable pageable);

    @Query(value = """
            select exists(
              select 1 from iys_consent_tasks newer
              join iys_consent_tasks current_task on current_task.id = :taskId
              where newer.user_id = current_task.user_id
                and newer.channel = current_task.channel
                and newer.decision_sequence > current_task.decision_sequence
                and newer.is_deleted = false)
            """, nativeQuery = true)
    boolean existsNewerDecision(@Param("taskId") UUID taskId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update iys_consent_tasks
            set status = 'PROCESSING', attempts = attempts + 1,
                processing_started_at = :startedAt, updated_at = :startedAt
            where id = :taskId and status = 'PENDING' and is_deleted = false
            """, nativeQuery = true)
    int claim(@Param("taskId") UUID taskId, @Param("startedAt") Instant startedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update iys_consent_tasks older
            set status = 'SUPERSEDED', completed_at = :completedAt, updated_at = :completedAt
            from iys_consent_tasks current_task
            where current_task.id = :taskId
              and older.user_id = current_task.user_id and older.channel = current_task.channel
              and older.decision_sequence < current_task.decision_sequence
              and older.status = 'PENDING' and older.is_deleted = false
            """, nativeQuery = true)
    int supersedeOlderPending(@Param("taskId") UUID taskId, @Param("completedAt") Instant completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update iys_consent_tasks set status = 'PENDING', processing_started_at = null,
                next_attempt_at = :now, updated_at = :now
            where status = 'PROCESSING' and processing_started_at <= :staleBefore and is_deleted = false
            """, nativeQuery = true)
    int recoverStaleClaims(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update iys_consent_tasks
            set retained_recipient_ciphertext = null, recipient_retain_until = null,
                status = 'EXHAUSTED', processing_started_at = null,
                last_failure_code = 'SECURE_RECIPIENT_UNAVAILABLE', updated_at = :now
            where retained_recipient_ciphertext is not null
              and recipient_retain_until <= :now and is_deleted = false
            """, nativeQuery = true)
    int expireRetainedRecipients(@Param("now") Instant now);
}

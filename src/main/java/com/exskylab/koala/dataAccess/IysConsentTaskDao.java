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

@Repository
public interface IysConsentTaskDao extends JpaRepository<IysConsentTask, UUID> {

    @EntityGraph(attributePaths = {"user", "consent"})
    java.util.Optional<IysConsentTask> findWithDetailsById(UUID id);

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
}

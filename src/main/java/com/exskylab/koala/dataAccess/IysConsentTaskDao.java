package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select (count(task) > 0) from IysConsentTask task
            where task.user.id = :userId
              and task.channel = :channel
              and task.consent.recordedAt > :recordedAt
            """)
    boolean existsNewerDecision(@Param("userId") UUID userId,
                                @Param("channel") NotificationChannel channel,
                                @Param("recordedAt") Instant recordedAt);
}

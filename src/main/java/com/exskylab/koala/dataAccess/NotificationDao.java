package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

@Repository
public interface NotificationDao extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findAllByRecipientIdAndChannelOrderByCreatedAtDesc(
            UUID recipientId, com.exskylab.koala.entities.NotificationChannel channel, Pageable pageable);

    long countByRecipientIdAndChannelAndReadAtIsNull(UUID recipientId,
                                                     com.exskylab.koala.entities.NotificationChannel channel);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :readAt
            where n.recipient.id = :recipientId and n.channel = :channel and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId,
                    @Param("channel") com.exskylab.koala.entities.NotificationChannel channel,
                    @Param("readAt") Instant readAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findForPushDelivery(@Param("id") UUID id);

    @Query("""
            select n.id from Notification n
            where n.channel = com.exskylab.koala.entities.NotificationChannel.PUSH
              and n.status = com.exskylab.koala.entities.NotificationStatus.QUEUED
              and n.sentAt is null
              and (n.pushAttemptCount > 0
                   or n.failureReason = 'TOKEN_SERIALIZATION_BUSY')
              and (n.pushNextAttemptAt is null or n.pushNextAttemptAt <= :now)
            order by n.pushNextAttemptAt asc nulls first, n.id asc
            """)
    List<UUID> findDuePushDeliveryIds(@Param("now") Instant now, Pageable pageable);

    @Query("""
            select n.id from Notification n
            where n.channel = com.exskylab.koala.entities.NotificationChannel.PUSH
              and n.status = com.exskylab.koala.entities.NotificationStatus.PROCESSING
              and n.pushProcessingStartedAt < :staleBefore
            order by n.pushProcessingStartedAt asc
            """)
    List<UUID> findStalePushDeliveryIds(@Param("staleBefore") Instant staleBefore, Pageable pageable);
}

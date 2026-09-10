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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'OPENED', updated_at = :openedAt
            where id = :notificationId and channel = 'EMAIL' and status = 'SENT'
              and sent_at is not null and is_deleted = false
            """, nativeQuery = true)
    int markDeliveredEmailAsOpened(@Param("notificationId") UUID notificationId,
                                   @Param("openedAt") Instant openedAt);

    @Query(value = """
            select id from notifications
            where status = 'QUEUED' and published_at is null and dispatch_payload is not null
              and next_dispatch_at <= :now and dispatch_claim_id is null
              and dispatch_attempts < :maxAttempts and is_deleted = false
            order by case dispatch_priority when 'HIGH' then 0 else 1 end,
                     next_dispatch_at asc, created_at asc
            """, nativeQuery = true)
    List<UUID> findDueDispatchIds(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                                  Pageable pageable);

    @Query(value = """
            select count(*) from notifications
            where status = 'QUEUED' and published_at is null and dispatch_payload is not null
              and is_deleted = false
            """, nativeQuery = true)
    long countDispatchBacklog();

    @Query(value = """
            select min(created_at) from notifications
            where status = 'QUEUED' and published_at is null and dispatch_payload is not null
              and is_deleted = false
            """, nativeQuery = true)
    Instant findOldestDispatchCreatedAt();

    @Query(value = """
            select count(*) from notifications
            where status = 'FAILED' and failure_reason = 'BROKER_PUBLISH_EXHAUSTED'
              and is_deleted = false
            """, nativeQuery = true)
    long countTerminalBrokerPublishFailures();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set dispatch_claim_id = :claimId, dispatch_claimed_at = :now,
                dispatch_attempts = dispatch_attempts + 1, updated_at = :now
            where id = :notificationId and status = 'QUEUED' and published_at is null
              and dispatch_payload is not null and next_dispatch_at <= :now
              and dispatch_claim_id is null and dispatch_attempts < :maxAttempts
              and is_deleted = false
            """, nativeQuery = true)
    int claimDispatch(@Param("notificationId") UUID notificationId, @Param("claimId") UUID claimId,
                      @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set published_at = :publishedAt, dispatch_claim_id = null, dispatch_claimed_at = null,
                failure_reason = case when failure_reason = 'BROKER_PUBLISH_RETRY_SCHEDULED'
                    then null else failure_reason end, updated_at = :publishedAt
            where id = :notificationId and dispatch_claim_id = :claimId and is_deleted = false
            """, nativeQuery = true)
    int completeDispatch(@Param("notificationId") UUID notificationId, @Param("claimId") UUID claimId,
                         @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = case when dispatch_attempts >= :maxAttempts then 'FAILED' else status end,
                failure_reason = case when dispatch_attempts >= :maxAttempts
                    then :exhaustedReason else :retryReason end,
                processed_at = case when dispatch_attempts >= :maxAttempts then :now else processed_at end,
                next_dispatch_at = case when dispatch_attempts >= :maxAttempts
                    then null else cast(:retryAt as timestamptz) end,
                dispatch_claim_id = null, dispatch_claimed_at = null, updated_at = :now
            where id = :notificationId and dispatch_claim_id = :claimId
              and status = 'QUEUED' and published_at is null and is_deleted = false
            """, nativeQuery = true)
    int failClaimedDispatch(@Param("notificationId") UUID notificationId, @Param("claimId") UUID claimId,
                            @Param("maxAttempts") int maxAttempts, @Param("retryAt") Instant retryAt,
                            @Param("now") Instant now, @Param("retryReason") String retryReason,
                            @Param("exhaustedReason") String exhaustedReason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'FAILED', processed_at = :now, failure_reason = :reason,
                next_dispatch_at = null, dispatch_claim_id = null, dispatch_claimed_at = null,
                updated_at = :now
            where id = :notificationId and dispatch_claim_id = :claimId
              and status = 'QUEUED' and published_at is null and is_deleted = false
            """, nativeQuery = true)
    int terminalizeClaimedDispatch(@Param("notificationId") UUID notificationId,
                                   @Param("claimId") UUID claimId, @Param("now") Instant now,
                                   @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set dispatch_attempts = greatest(dispatch_attempts - 1, 0),
                dispatch_claim_id = null, dispatch_claimed_at = null,
                next_dispatch_at = :now, updated_at = :now
            where status = 'QUEUED' and published_at is null and dispatch_payload is not null
              and dispatch_claimed_at <= :staleBefore
              and is_deleted = false
            """, nativeQuery = true)
    int releaseStaleDispatchClaims(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'FAILED', failure_reason = :reason, processed_at = :now,
                next_dispatch_at = null, updated_at = :now
            where status = 'QUEUED' and published_at is null and dispatch_payload is not null
              and dispatch_claim_id is null and dispatch_attempts >= :maxAttempts
              and is_deleted = false
            """, nativeQuery = true)
    int terminalizeExhaustedDispatches(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now,
                                       @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'FAILED', failure_reason = :reason, processed_at = :now, updated_at = :now
            where status = 'QUEUED' and channel in ('EMAIL', 'SMS', 'PUSH')
              and dispatch_payload is null and created_at <= :createdBefore and is_deleted = false
            """, nativeQuery = true)
    int terminalizeLegacyQueued(@Param("createdBefore") Instant createdBefore, @Param("now") Instant now,
                                @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications set status = 'PROCESSING', processed_at = :startedAt,
                delivery_claim_id = :claimId, delivery_claimed_at = :startedAt, updated_at = :startedAt
            where id = :notificationId and channel = :channel
              and (status = 'QUEUED' or (status = 'PROCESSING'
                  and (delivery_claimed_at is null or delivery_claimed_at <= :staleBefore)))
              and sent_at is null and is_deleted = false
            """, nativeQuery = true)
    int claimDelivery(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                      @Param("claimId") UUID claimId, @Param("startedAt") Instant startedAt,
                      @Param("staleBefore") Instant staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications set status = 'SENT', sent_at = :now, processed_at = :now,
                external_id = :externalId, failure_reason = null,
                delivery_claim_id = null, delivery_claimed_at = null, updated_at = :now
            where id = :notificationId and channel = :channel and status = 'PROCESSING'
              and delivery_claim_id = :claimId and sent_at is null and is_deleted = false
            """, nativeQuery = true)
    int completeDelivery(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                         @Param("claimId") UUID claimId, @Param("externalId") String externalId,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications set status = 'FAILED', processed_at = :now, failure_reason = :reason,
                delivery_claim_id = null, delivery_claimed_at = null, updated_at = :now
            where id = :notificationId and channel = :channel and status = 'PROCESSING'
              and delivery_claim_id = :claimId and sent_at is null and is_deleted = false
            """, nativeQuery = true)
    int failDelivery(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                     @Param("claimId") UUID claimId, @Param("reason") String reason,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications set status = 'SUPPRESSED', processed_at = :now, failure_reason = :reason,
                delivery_claim_id = null, delivery_claimed_at = null, updated_at = :now
            where id = :notificationId and channel = :channel and status = 'PROCESSING'
              and delivery_claim_id = :claimId and sent_at is null and is_deleted = false
            """, nativeQuery = true)
    int suppressDelivery(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                         @Param("claimId") UUID claimId, @Param("reason") String reason,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'QUEUED', failure_reason = :reason,
                delivery_claim_id = null, delivery_claimed_at = null, updated_at = :now
            where id = :notificationId and channel = :channel and status = 'PROCESSING'
              and delivery_claim_id = :claimId and sent_at is null and is_deleted = false
            """, nativeQuery = true)
    int releaseDeliveryForRetry(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                                @Param("claimId") UUID claimId, @Param("reason") String reason,
                                @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notifications
            set status = 'FAILED', failure_reason = :reason, processed_at = :now, updated_at = :now
            where id = :notificationId and channel = :channel and status = 'QUEUED'
              and sent_at is null and delivery_claim_id is null and is_deleted = false
            """, nativeQuery = true)
    int terminalizeDelivery(@Param("notificationId") UUID notificationId, @Param("channel") String channel,
                            @Param("reason") String reason, @Param("now") Instant now);
}

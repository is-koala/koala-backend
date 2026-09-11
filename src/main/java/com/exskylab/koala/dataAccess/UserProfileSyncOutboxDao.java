package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.UserProfileSyncOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileSyncOutboxDao extends JpaRepository<UserProfileSyncOutbox, UUID> {

    @Query(value = """
            select id from user_profile_sync_outbox
            where user_id = :userId and status = 'PENDING' and published_at is null
              and claim_id is null and is_deleted = false
            order by created_at asc
            limit 1
            """, nativeQuery = true)
    Optional<UUID> findUnclaimedUnpublishedId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set payload = :payload, next_attempt_at = :now, failure_reason = null,
                dispatch_request_id = :requestId, dispatch_user_id = :userContext,
                dispatch_session_id = :sessionId, dispatch_device_id = :deviceId,
                updated_at = :now
            where id = :intentId and status = 'PENDING' and published_at is null
              and claim_id is null and is_deleted = false
            """, nativeQuery = true)
    int replaceUnpublishedPayload(@Param("intentId") UUID intentId, @Param("payload") String payload,
                                  @Param("now") Instant now, @Param("requestId") String requestId,
                                  @Param("userContext") String userContext,
                                  @Param("sessionId") String sessionId, @Param("deviceId") String deviceId);

    @Query(value = """
            select id from user_profile_sync_outbox
            where status = 'PENDING' and published_at is null and payload is not null
              and next_attempt_at <= :now and claim_id is null
              and attempts < :maxAttempts and is_deleted = false
            order by next_attempt_at asc, created_at asc
            """, nativeQuery = true)
    List<UUID> findDueDispatchIds(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                                  Pageable pageable);

    @Query(value = """
            select count(*) from user_profile_sync_outbox
            where status = 'PENDING' and published_at is null and payload is not null
              and is_deleted = false
            """, nativeQuery = true)
    long countDispatchBacklog();

    @Query(value = """
            select min(created_at) from user_profile_sync_outbox
            where status = 'PENDING' and published_at is null and payload is not null
              and is_deleted = false
            """, nativeQuery = true)
    Instant findOldestDispatchCreatedAt();

    @Query(value = """
            select count(*) from user_profile_sync_outbox
            where status = 'FAILED' and failure_reason = 'BROKER_PUBLISH_EXHAUSTED'
              and is_deleted = false
            """, nativeQuery = true)
    long countTerminalBrokerPublishFailures();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set claim_id = :claimId, claimed_at = :now, attempts = attempts + 1, updated_at = :now
            where id = :intentId and status = 'PENDING' and published_at is null
              and payload is not null and next_attempt_at <= :now
              and claim_id is null and attempts < :maxAttempts and is_deleted = false
            """, nativeQuery = true)
    int claimDispatch(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                      @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set published_at = :publishedAt, claim_id = null, claimed_at = null,
                failure_reason = case when failure_reason = 'BROKER_PUBLISH_RETRY_SCHEDULED'
                    then null else failure_reason end, updated_at = :publishedAt
            where id = :intentId and claim_id = :claimId and is_deleted = false
            """, nativeQuery = true)
    int completeDispatch(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                         @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set status = case when attempts >= :maxAttempts then 'FAILED' else status end,
                failure_reason = case when attempts >= :maxAttempts
                    then :exhaustedReason else :retryReason end,
                next_attempt_at = case when attempts >= :maxAttempts
                    then next_attempt_at else cast(:retryAt as timestamptz) end,
                claim_id = null, claimed_at = null, updated_at = :now
            where id = :intentId and claim_id = :claimId
              and status = 'PENDING' and published_at is null and is_deleted = false
            """, nativeQuery = true)
    int failClaimedDispatch(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                            @Param("maxAttempts") int maxAttempts, @Param("retryAt") Instant retryAt,
                            @Param("now") Instant now, @Param("retryReason") String retryReason,
                            @Param("exhaustedReason") String exhaustedReason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set status = 'FAILED', failure_reason = :reason, next_attempt_at = next_attempt_at,
                claim_id = null, claimed_at = null, updated_at = :now
            where id = :intentId and claim_id = :claimId
              and status = 'PENDING' and published_at is null and is_deleted = false
            """, nativeQuery = true)
    int terminalizeClaimedDispatch(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                                   @Param("now") Instant now, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set attempts = greatest(attempts - 1, 0), claim_id = null, claimed_at = null,
                next_attempt_at = :now, updated_at = :now
            where status = 'PENDING' and published_at is null and payload is not null
              and claimed_at <= :staleBefore and is_deleted = false
            """, nativeQuery = true)
    int releaseStaleDispatchClaims(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set status = 'FAILED', failure_reason = :reason, updated_at = :now
            where status = 'PENDING' and published_at is null and payload is not null
              and claim_id is null and attempts >= :maxAttempts and is_deleted = false
            """, nativeQuery = true)
    int terminalizeExhaustedDispatches(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now,
                                       @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set processing_claim_id = :claimId, processing_claimed_at = :now, updated_at = :now
            where id = :intentId and processed_at is null
              and (processing_claim_id is null or processing_claimed_at <= :staleBefore)
              and is_deleted = false
            """, nativeQuery = true)
    int claimProcessing(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                        @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set processed_at = :now, processing_claim_id = null, processing_claimed_at = null,
                failure_reason = null, updated_at = :now
            where id = :intentId and processing_claim_id = :claimId and processed_at is null
              and is_deleted = false
            """, nativeQuery = true)
    int completeProcessing(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                           @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update user_profile_sync_outbox
            set processing_claim_id = null, processing_claimed_at = null, updated_at = :now
            where id = :intentId and processing_claim_id = :claimId and processed_at is null
              and is_deleted = false
            """, nativeQuery = true)
    int releaseProcessingClaim(@Param("intentId") UUID intentId, @Param("claimId") UUID claimId,
                               @Param("now") Instant now);
}

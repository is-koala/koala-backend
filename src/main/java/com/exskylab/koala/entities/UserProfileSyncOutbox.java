package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@SQLDelete(sql = "UPDATE user_profile_sync_outbox SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@Table(name = "user_profile_sync_outbox")
public class UserProfileSyncOutbox extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserProfileSyncStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_claimed_at")
    private Instant processingClaimedAt;

    @Column(name = "processing_claim_id")
    private UUID processingClaimId;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "dispatch_request_id", length = 36)
    private String dispatchRequestId;

    @Column(name = "dispatch_user_id", length = 36)
    private String dispatchUserId;

    @Column(name = "dispatch_session_id", length = 36)
    private String dispatchSessionId;

    @Column(name = "dispatch_device_id", length = 36)
    private String dispatchDeviceId;
}

package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@SQLDelete(sql = "UPDATE notifications SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@Table(name = "notifications")
public class Notification extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    private NotificationCategory category;

    @Column(name = "destination_phone_number")
    private String destinationPhoneNumber;

    @Column(name = "destination_email")
    private String destinationEmail;

    @Column(name = "destination_push_token")
    private String destinationPushToken;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "template_parameters")
    private String templateParameters;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_priority", length = 16)
    private DispatchPriority dispatchPriority;

    @Column(name = "dispatch_payload", columnDefinition = "TEXT")
    private String dispatchPayload;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @Column(name = "next_dispatch_at")
    private Instant nextDispatchAt;

    @Column(name = "dispatch_claimed_at")
    private Instant dispatchClaimedAt;

    @Column(name = "dispatch_claim_id")
    private UUID dispatchClaimId;

    @Column(name = "dispatch_request_id", length = 36)
    private String dispatchRequestId;

    @Column(name = "dispatch_user_id", length = 36)
    private String dispatchUserId;

    @Column(name = "dispatch_session_id", length = 36)
    private String dispatchSessionId;

    @Column(name = "dispatch_device_id", length = 36)
    private String dispatchDeviceId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "delivery_claimed_at")
    private Instant deliveryClaimedAt;

    @Column(name = "delivery_claim_id")
    private UUID deliveryClaimId;

}

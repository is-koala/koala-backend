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

    @Column(name = "destination_push_token", length = 512)
    private String destinationPushToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_device_id")
    private Device destinationDevice;

    @Column(name = "push_attempt_count", nullable = false)
    private int pushAttemptCount;

    @Column(name = "push_data_payload", columnDefinition = "TEXT")
    private String pushDataPayload;

    @Column(name = "push_next_attempt_at")
    private Instant pushNextAttemptAt;

    @Column(name = "push_processing_started_at")
    private Instant pushProcessingStartedAt;

    @Column(name = "push_processing_claim_id")
    private UUID pushProcessingClaimId;

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

    @PrePersist
    @PreUpdate
    void removePushTokenSnapshot() {
        if (channel == NotificationChannel.PUSH) {
            destinationPushToken = null;
        }
    }

}

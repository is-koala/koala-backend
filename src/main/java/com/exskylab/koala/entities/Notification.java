package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
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

    @Column(name = "failure_reason")
    private String failureReason;

}

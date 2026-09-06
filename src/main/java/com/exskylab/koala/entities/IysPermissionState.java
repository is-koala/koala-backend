package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@SQLDelete(sql = "UPDATE iys_permission_states SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@Table(name = "iys_permission_states")
public class IysPermissionState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false, length = 320)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IysPermissionStatus status;

    @Column(name = "provider_recorded_at", nullable = false)
    private Instant providerRecordedAt;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "provider_source", length = 32)
    private String providerSource;

    @Column(name = "next_reconciliation_at", nullable = false)
    private Instant nextReconciliationAt;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_failure_code", length = 32)
    private IysFailureCode lastFailureCode;
}

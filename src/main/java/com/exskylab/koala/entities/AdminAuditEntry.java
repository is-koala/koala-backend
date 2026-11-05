package com.exskylab.koala.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "admin_audit_entries")
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_staff_id", updatable = false)
    private UUID actorStaffId;

    @Column(name = "actor_display_name", nullable = false, updatable = false, length = 200)
    private String actorDisplayName;

    @Column(name = "permission", nullable = false, updatable = false, length = 64)
    private String permission;

    @Column(name = "action", nullable = false, updatable = false, length = 96)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 128)
    private String resourceId;

    @Column(name = "allowed", nullable = false, updatable = false)
    private boolean allowed;

    @Column(name = "mutating", nullable = false, updatable = false)
    private boolean mutating;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", nullable = false, updatable = false, length = 16)
    private PermissionSensitivity sensitivity;

    @Column(name = "retain_until", nullable = false, updatable = false)
    private Instant retainUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", updatable = false, length = 16)
    private AuditSubjectType subjectType;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_subject_type", updatable = false, length = 16)
    private AuditSubjectType relatedSubjectType;

    @Column(name = "related_subject_id", updatable = false)
    private UUID relatedSubjectId;

    @Column(name = "row_count", updatable = false)
    private Integer rowCount;

    @Column(name = "pii_included", updatable = false)
    private Boolean piiIncluded;

    @OneToOne(mappedBy = "entry", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private AdminAuditEntryDetail detail;
}

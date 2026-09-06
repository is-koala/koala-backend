package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
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
@Table(name = "admin_audit_entry_details")
public class AdminAuditEntryDetail {

    @Id
    @Column(name = "audit_entry_id")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_entry_id")
    private AdminAuditEntry entry;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "before_value", columnDefinition = "text")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "text")
    private String afterValue;

    @Column(name = "purge_after", nullable = false)
    private Instant purgeAfter;
}

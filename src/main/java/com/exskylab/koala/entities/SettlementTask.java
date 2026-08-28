package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@SQLDelete(sql = "UPDATE settlement_tasks SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "settlement_tasks")
public class SettlementTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_assignment_id")
    private JobAssignment jobAssignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private SettlementOperation operation;

    @Column(name = "provider_transaction_id", nullable = false, length = 128)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementTaskStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "amount", precision = 19, scale = 2)
    private java.math.BigDecimal amount;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;
}

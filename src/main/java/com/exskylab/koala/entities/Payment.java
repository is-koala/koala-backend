package com.exskylab.koala.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SQLDelete(sql = "UPDATE payments SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_assignment_id", nullable = false)
    private JobAssignment jobAssignment;

    @Column(name = "conversation_id", nullable = false, unique = true, length = 64)
    private String conversationId;

    @Column(name = "provider_token", length = 512)
    private String providerToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentAttemptStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "pricing_version", length = 32)
    private String pricingVersion;

    @Column(name = "worker_payout", precision = 19, scale = 2)
    private BigDecimal workerPayout;

    @Column(name = "commission_amount", precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "commission_rate", precision = 6, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "total_charged", precision = 19, scale = 2)
    private BigDecimal totalCharged;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "completed_at")
    private Instant completedAt;
}

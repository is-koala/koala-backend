package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SQLDelete(sql = "UPDATE cancellations SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "cancellations")
public class Cancellation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_assignment_id", nullable = false)
    private JobAssignment jobAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancelled_by_id", nullable = false)
    private User cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by_party", nullable = false, length = 16)
    private CancellationParty cancelledByParty;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "notice_minutes", nullable = false)
    private long noticeMinutes;

    @Column(name = "fee_rate", precision = 6, scale = 4, nullable = false)
    private BigDecimal feeRate;

    @Column(name = "fee_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "refund_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", unique = true)
    private Refund refund;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;
}

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
@SQLDelete(sql = "UPDATE job_assignments SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Table(name ="job_assignments")
public class JobAssignment extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", referencedColumnName = "id", unique = true)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name = "assignment_date_time")
    private Instant assignmentDateTime;

    @Column(name = "expected_completion_date_time")
    private Instant expectedCompletionDateTime;

    @Column(name = "actual_completion_date_time")
    private Instant actualCompletionDateTime;

    @Column(name = "work_hours")
    private int workHours;

    @Column(name = "final_payout", precision = 19, scale = 2)
    private BigDecimal finalPayout;

    @Column(name = "worker_payout", precision = 19, scale = 2)
    private BigDecimal workerPayout;

    @Column(name = "commission_amount", precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "commission_rate", precision = 6, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "total_charged", precision = 19, scale = 2)
    private BigDecimal totalCharged;

    @Column(name = "notes")
    private String notes;

    @Column(name = "iyzico_submerchant_key")
    private String iyzicoSubmerchantKey;

}

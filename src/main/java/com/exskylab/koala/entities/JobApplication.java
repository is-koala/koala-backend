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

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SQLDelete(sql = "UPDATE job_applications SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "job_applications")
public class JobApplication extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "application_date_time")
    private Instant applicationDateTime;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "answered_date_time")
    private Instant answeredDateTime;

    @OneToOne(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private JobAssignment assignment;



}

package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SQLDelete(sql = "UPDATE transport_plans SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "transport_plans")
public class TransportPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbound_method", nullable = false, length = 32)
    private TransportMethod outboundMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_method", nullable = false, length = 32)
    private TransportMethod returnMethod;

    @Column(name = "allowance_amount", precision = 19, scale = 2)
    private BigDecimal allowanceAmount;

    @Column(name = "allowance_note", length = 500)
    private String allowanceNote;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<Shuttle> shuttles = new ArrayList<>();
}

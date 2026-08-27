package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@SQLDelete(sql = "UPDATE job_templates SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "job_templates")
public class JobTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_category_id")
    private JobCategory category;

    @Column(name = "title")
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "sector")
    private String sector;

    @Column(name = "position")
    private String position;

    @Column(name = "duties", length = 2000)
    private String duties;

    @Column(name = "dress_code")
    private String dressCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 32)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", length = 32)
    private SalaryType salaryType;

    @Column(name = "salary", precision = 19, scale = 2)
    private BigDecimal salary;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "experience_required")
    private Boolean experienceRequired;

    @Column(name = "insurance_required")
    private Boolean insuranceRequired;
}

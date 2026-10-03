package com.exskylab.koala.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SQLDelete(sql = "UPDATE shuttles SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "shuttles")
public class Shuttle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TransportPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private TransportDirection direction;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "plate", length = 20)
    private String plate;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "driver_phone", length = 32)
    private String driverPhone;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "qr_secret", nullable = false, length = 64, updatable = false)
    private String qrSecret;

    @OneToMany(mappedBy = "shuttle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ShuttleStop> stops = new ArrayList<>();
}

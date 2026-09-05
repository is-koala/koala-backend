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

@SQLDelete(sql = "UPDATE addresses SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "addresses")
public class Address extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(name = "neighborhood", length = 120)
    private String neighborhood;

    @Column(name = "thoroughfare", length = 160)
    private String thoroughfare;

    @Column(name = "site_name", length = 120)
    private String siteName;

    @Column(name = "block", length = 40)
    private String block;

    @Column(name = "building_name", length = 120)
    private String buildingName;

    @Column(name = "building_number", length = 30)
    private String buildingNumber;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "unit_number", length = 30)
    private String unitNumber;

    @Column(name = "postal_code", length = 5)
    private String postalCode;

    @Column(name = "additional_directions", length = 500)
    private String additionalDirections;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_source", length = 40)
    private AddressPositionSource positionSource;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "canonical_version", nullable = false)
    private int canonicalVersion;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;
}

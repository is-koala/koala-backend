package com.exskylab.koala.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@SQLDelete(sql = "UPDATE boarding_events SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "boarding_events")
public class BoardingEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_assignment_id", nullable = false, updatable = false)
    private SeatAssignment seatAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_assignment_id", nullable = false, updatable = false)
    private JobAssignment jobAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shuttle_id", nullable = false, updatable = false)
    private Shuttle shuttle;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private BoardingKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private BoardingSource source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "boarding_day", nullable = false)
    private LocalDate boardingDay;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Convert(converter = TimesheetPlausibilityFlagsConverter.class)
    @Column(name = "flags", nullable = false)
    private Set<TimesheetPlausibilityFlag> flags = EnumSet.noneOf(TimesheetPlausibilityFlag.class);

    @Column(name = "client_event_id")
    private UUID clientEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_id", nullable = false)
    private User recordedBy;
}

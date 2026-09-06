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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@SQLDelete(sql = "UPDATE work_sessions SET is_deleted = true, deleted_at = now() WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "work_sessions")
public class WorkSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_assignment_id", nullable = false)
    private JobAssignment jobAssignment;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_in_client_event_id")
    private UUID checkInClientEventId;

    @Convert(converter = RawInstantConverter.class)
    @Column(name = "check_in_captured_at", length = 40)
    private Instant checkInCapturedAt;

    @Column(name = "check_in_received_at")
    private Instant checkInReceivedAt;

    @Convert(converter = TimesheetPlausibilityFlagsConverter.class)
    @Column(name = "check_in_plausibility_flags")
    private Set<TimesheetPlausibilityFlag> checkInPlausibilityFlags =
            EnumSet.noneOf(TimesheetPlausibilityFlag.class);

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "check_out_client_event_id")
    private UUID checkOutClientEventId;

    @Convert(converter = RawInstantConverter.class)
    @Column(name = "check_out_captured_at", length = 40)
    private Instant checkOutCapturedAt;

    @Column(name = "check_out_received_at")
    private Instant checkOutReceivedAt;

    @Convert(converter = TimesheetPlausibilityFlagsConverter.class)
    @Column(name = "check_out_plausibility_flags")
    private Set<TimesheetPlausibilityFlag> checkOutPlausibilityFlags =
            EnumSet.noneOf(TimesheetPlausibilityFlag.class);

    @Column(name = "check_in_latitude", precision = 9, scale = 6)
    private BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 9, scale = 6)
    private BigDecimal checkInLongitude;

    @Column(name = "check_out_latitude", precision = 9, scale = 6)
    private BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 9, scale = 6)
    private BigDecimal checkOutLongitude;

    @Column(name = "minutes")
    private Integer minutes;
}

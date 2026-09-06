package com.exskylab.koala.core.dtos.timesheet.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.exskylab.koala.entities.TimesheetPlausibilityFlag;

public record WorkSessionDto(UUID id, UUID jobAssignmentId, Instant checkInAt, Instant checkOutAt,
                             UUID checkInClientEventId, Instant checkInCapturedAt, Instant checkInReceivedAt,
                             Set<TimesheetPlausibilityFlag> checkInPlausibilityFlags,
                             UUID checkOutClientEventId, Instant checkOutCapturedAt, Instant checkOutReceivedAt,
                             Set<TimesheetPlausibilityFlag> checkOutPlausibilityFlags,
                             BigDecimal checkInLatitude, BigDecimal checkInLongitude,
                             BigDecimal checkOutLatitude, BigDecimal checkOutLongitude,
                             Integer minutes) {
}

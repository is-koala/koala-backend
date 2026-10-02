package com.exskylab.koala.core.dtos.timesheet.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkSessionDto(UUID id, UUID jobAssignmentId, Instant checkInAt, Instant checkOutAt,
                             UUID checkInClientEventId, Instant checkInCapturedAt, Instant checkInReceivedAt,
                             @ArraySchema(schema = @Schema(implementation = String.class,
                                     allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "OUTSIDE_JOB_WINDOW", "NON_MONOTONIC"}))
                             Set<TimesheetPlausibilityFlag> checkInPlausibilityFlags,
                             UUID checkOutClientEventId, Instant checkOutCapturedAt, Instant checkOutReceivedAt,
                             @ArraySchema(schema = @Schema(implementation = String.class,
                                     allowableValues = {"DEVICE_TIME_IMPLAUSIBLE", "OUTSIDE_JOB_WINDOW", "NON_MONOTONIC"}))
                             Set<TimesheetPlausibilityFlag> checkOutPlausibilityFlags,
                             BigDecimal checkInLatitude, BigDecimal checkInLongitude,
                             BigDecimal checkOutLatitude, BigDecimal checkOutLongitude,
                             Integer minutes) {
}

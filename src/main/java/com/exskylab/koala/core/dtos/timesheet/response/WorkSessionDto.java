package com.exskylab.koala.core.dtos.timesheet.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkSessionDto(UUID id, UUID jobAssignmentId, Instant checkInAt, Instant checkOutAt,
                             BigDecimal checkInLatitude, BigDecimal checkInLongitude,
                             BigDecimal checkOutLatitude, BigDecimal checkOutLongitude,
                             Integer minutes) {
}

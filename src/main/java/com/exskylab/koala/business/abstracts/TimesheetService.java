package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.WorkSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimesheetService {

    WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude);

    WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude,
                        UUID clientEventId, Instant capturedAt);

    WorkSession checkOut(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude);

    WorkSession checkOut(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude,
                         UUID clientEventId, Instant capturedAt);

    List<WorkSession> getSessions(UUID jobAssignmentId);
}

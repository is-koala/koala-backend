package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.WorkSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TimesheetService {

    WorkSession checkIn(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude);

    WorkSession checkOut(UUID jobAssignmentId, BigDecimal latitude, BigDecimal longitude);

    List<WorkSession> getSessions(UUID jobAssignmentId);
}

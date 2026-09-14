package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.entities.WorkSession;

import java.util.List;
import java.util.UUID;

public interface TimesheetService {

    WorkSession checkIn(UUID jobAssignmentId, CheckPointDto point);

    WorkSession checkOut(UUID jobAssignmentId, CheckPointDto point);

    List<WorkSession> getSessions(UUID jobAssignmentId);

    List<WorkSession> getSessionsForStaff(UUID jobAssignmentId);
}

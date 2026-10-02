package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminTimelineEventDto(String type, Instant at, UUID id, BoardingKind kind, BoardingSource source,
                                    Set<TimesheetPlausibilityFlag> flags, UUID recordedById, UUID shuttleId,
                                    UUID seatAssignmentId) {
}

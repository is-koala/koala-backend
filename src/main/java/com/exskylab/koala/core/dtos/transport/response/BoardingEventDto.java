package com.exskylab.koala.core.dtos.transport.response;

import com.exskylab.koala.entities.BoardingKind;
import com.exskylab.koala.entities.BoardingSource;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoardingEventDto {

    private UUID id;
    private UUID seatAssignmentId;
    private BoardingKind kind;
    private BoardingSource source;
    private Instant occurredAt;
    private Instant capturedAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Set<TimesheetPlausibilityFlag> flags;
    private UUID recordedById;
    private UUID shuttleId;
}

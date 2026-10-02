package com.exskylab.koala.core.dtos.transport.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShuttleBoardingRowDto {

    private UUID seatAssignmentId;
    private UUID jobAssignmentId;
    private UUID workerId;
    private String firstName;
    private String lastName;
    private UUID stopId;
    private BoardingEventDto lastEvent;
    private boolean boarded;
}

package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminTimelineEventDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.dtos.transport.response.JobTransportDto;
import com.exskylab.koala.core.dtos.transport.response.PublicShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.PublicStopDto;
import com.exskylab.koala.core.dtos.transport.response.SeatAssignmentDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleBoardingRowDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleStopDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.entities.BoardingEvent;
import com.exskylab.koala.entities.SeatAssignment;
import com.exskylab.koala.entities.Shuttle;
import com.exskylab.koala.entities.ShuttleStop;
import com.exskylab.koala.entities.TransportPlan;

import java.util.UUID;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AddressMapper.class}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TransportMapper {

    @Mapping(target = "jobId", source = "job.id")
    @Mapping(target = "shuttles", ignore = true)
    TransportPlanDto toTransportPlanDto(TransportPlan plan);

    @Mapping(target = "shuttles", ignore = true)
    @Mapping(target = "seats", ignore = true)
    AssignmentTransportDto toAssignmentTransportDto(TransportPlan plan);

    @Mapping(target = "shuttleCount", ignore = true)
    @Mapping(target = "shuttles", ignore = true)
    JobTransportDto toJobTransportDto(TransportPlan plan);

    @Mapping(target = "stops", ignore = true)
    @Mapping(target = "seatsTaken", ignore = true)
    ShuttleDto toShuttleDto(Shuttle shuttle);

    @Mapping(target = "seatsTaken", ignore = true)
    ShuttleStopDto toShuttleStopDto(ShuttleStop stop);

    @Mapping(target = "stops", ignore = true)
    PublicShuttleDto toPublicShuttleDto(Shuttle shuttle);

    @Mapping(target = "districtName", source = "address.district.name")
    @Mapping(target = "neighborhood", source = "address.neighborhood")
    PublicStopDto toPublicStopDto(ShuttleStop stop);

    @Mapping(target = "shuttleId", source = "shuttle.id")
    @Mapping(target = "stopId", source = "stop.id")
    SeatAssignmentDto toSeatAssignmentDto(SeatAssignment seat);

    @Mapping(target = "seatAssignmentId", source = "event.seatAssignment.id")
    @Mapping(target = "recordedById", source = "event.recordedBy.id")
    @Mapping(target = "shuttleId", source = "shuttleId")
    BoardingEventDto toBoardingEventDto(BoardingEvent event, UUID shuttleId);

    @Mapping(target = "seatAssignmentId", source = "seat.id")
    @Mapping(target = "jobAssignmentId", source = "seat.jobAssignment.id")
    @Mapping(target = "workerId", source = "workerId")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "stopId", source = "seat.stop.id")
    @Mapping(target = "lastEvent", source = "lastEvent")
    @Mapping(target = "boarded", source = "boarded")
    ShuttleBoardingRowDto toShuttleBoardingRowDto(SeatAssignment seat, UUID workerId, String firstName, String lastName,
                                                  BoardingEventDto lastEvent, boolean boarded);

    @Mapping(target = "type", constant = "SEAT")
    @Mapping(target = "at", source = "createdAt")
    @Mapping(target = "kind", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "flags", ignore = true)
    @Mapping(target = "recordedById", ignore = true)
    @Mapping(target = "shuttleId", source = "shuttle.id")
    @Mapping(target = "seatAssignmentId", source = "id")
    AdminTimelineEventDto toSeatTimelineEvent(SeatAssignment seat);

    @Mapping(target = "type", constant = "BOARDING")
    @Mapping(target = "at", source = "event.occurredAt")
    @Mapping(target = "id", source = "event.id")
    @Mapping(target = "kind", source = "event.kind")
    @Mapping(target = "source", source = "event.source")
    @Mapping(target = "flags", source = "event.flags")
    @Mapping(target = "recordedById", source = "event.recordedBy.id")
    @Mapping(target = "shuttleId", source = "shuttleId")
    @Mapping(target = "seatAssignmentId", source = "event.seatAssignment.id")
    AdminTimelineEventDto toBoardingTimelineEvent(BoardingEvent event, UUID shuttleId);
}

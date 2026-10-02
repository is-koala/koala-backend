package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.jobAssignment.response.AdminTimelineEventDto;
import com.exskylab.koala.core.dtos.transport.request.SeatBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.SeatSelectionDto;
import com.exskylab.koala.core.dtos.transport.request.ShuttleBoardingDto;
import com.exskylab.koala.core.dtos.transport.request.TransportPlanWriteDto;
import com.exskylab.koala.core.dtos.transport.response.AssignmentTransportDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingEventDto;
import com.exskylab.koala.core.dtos.transport.response.BoardingQrDto;
import com.exskylab.koala.core.dtos.transport.response.JobTransportDto;
import com.exskylab.koala.core.dtos.transport.response.ShuttleBoardingRowDto;
import com.exskylab.koala.core.dtos.transport.response.TransportPlanDto;
import com.exskylab.koala.entities.JobAssignment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TransportService {

    TransportPlanDto writePlan(UUID jobId, TransportPlanWriteDto request);

    TransportPlanDto getPlan(UUID jobId);

    /**
     * Personel görünümü: işveren kontrolü yok. İlan yoksa JOB_NOT_FOUND,
     * plan yoksa PLAN_NOT_FOUND. Plaka ve şoför her serviste dolu.
     */
    TransportPlanDto getPlanForStaff(UUID jobId);

    AssignmentTransportDto getAssignmentTransport(UUID assignmentId);

    AssignmentTransportDto selectSeat(UUID assignmentId, SeatSelectionDto request);

    Optional<JobTransportDto> publicSummaryFor(UUID jobId);

    Set<UUID> jobIdsWithShuttle(Collection<UUID> jobIds);

    Map<UUID, AssignmentTransportDto> viewsForAssignments(List<JobAssignment> assignments, UUID viewerId);

    BoardingQrDto issueShuttleQr(UUID shuttleId, boolean staticQr);

    BoardingQrDto issueWorkerQr(UUID assignmentId);

    BoardingEventDto boardOnShuttle(UUID shuttleId, ShuttleBoardingDto request);

    BoardingEventDto markSeatBoarding(UUID seatAssignmentId, SeatBoardingDto request);

    List<ShuttleBoardingRowDto> listBoardings(UUID shuttleId);

    List<AdminTimelineEventDto> timelineForStaff(UUID assignmentId);

    List<UUID> findEveningReminderDueIds(Instant now);

    List<UUID> findDepartureReminderDueIds(Instant now);

    boolean sendEveningReminder(UUID seatId, Instant now);

    boolean sendDepartureReminder(UUID seatId, Instant now);

    List<UUID> findBoardingReminderDueIds(Instant now);

    List<UUID> findNotBoardedNoticeDueIds(Instant now);

    boolean sendBoardingReminder(UUID seatId, Instant now);

    boolean sendNotBoardedNotice(UUID seatId, Instant now);
}

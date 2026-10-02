package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.SeatAssignment;
import com.exskylab.koala.entities.TransportDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatAssignmentDao extends JpaRepository<SeatAssignment, UUID> {

    Optional<SeatAssignment> findByJobAssignmentIdAndDirection(UUID jobAssignmentId, TransportDirection direction);

    List<SeatAssignment> findAllByJobAssignmentIdIn(Collection<UUID> jobAssignmentIds);

    List<SeatAssignment> findAllByShuttleIdIn(Collection<UUID> shuttleIds);

    List<SeatAssignment> findAllByStopIdIn(Collection<UUID> stopIds);

    long countByShuttleId(UUID shuttleId);

    @Query("""
            select s.shuttle.id as shuttleId, count(s) as total
            from SeatAssignment s
            where s.shuttle.id in :shuttleIds
            group by s.shuttle.id
            """)
    List<ShuttleSeatCount> countByShuttleIds(@Param("shuttleIds") Collection<UUID> shuttleIds);

    @Query("""
            select s.stop.id as stopId, count(s) as total
            from SeatAssignment s
            where s.stop.id in :stopIds
            group by s.stop.id
            """)
    List<StopSeatCount> countByStopIds(@Param("stopIds") Collection<UUID> stopIds);

    @Query("""
            select s.id from SeatAssignment s
            where s.eveningReminderSentAt is null
              and s.stop.scheduledAt >= :from and s.stop.scheduledAt < :to
              and s.jobAssignment.status in :statuses
            order by s.stop.scheduledAt asc
            """)
    List<UUID> findEveningReminderDueIds(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("statuses") Collection<AssignmentStatus> statuses,
                                         Pageable pageable);

    @Query("""
            select s.id from SeatAssignment s
            where s.departureReminderSentAt is null
              and s.stop.scheduledAt > :from and s.stop.scheduledAt <= :to
              and s.jobAssignment.status in :statuses
            order by s.stop.scheduledAt asc
            """)
    List<UUID> findDepartureReminderDueIds(@Param("from") Instant from, @Param("to") Instant to,
                                           @Param("statuses") Collection<AssignmentStatus> statuses,
                                           Pageable pageable);

    @Query("""
            select s.id from SeatAssignment s
            where s.boardingReminderSentAt is null
              and s.direction = :direction
              and s.stop.scheduledAt > :from and s.stop.scheduledAt <= :to
              and s.jobAssignment.status in :statuses
            order by s.stop.scheduledAt asc
            """)
    List<UUID> findBoardingReminderDueIds(@Param("from") Instant from, @Param("to") Instant to,
                                          @Param("direction") TransportDirection direction,
                                          @Param("statuses") Collection<AssignmentStatus> statuses,
                                          Pageable pageable);

    @Modifying
    @Query("update SeatAssignment s set s.eveningReminderSentAt = :now where s.id = :id and s.eveningReminderSentAt is null")
    int claimEveningReminder(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update SeatAssignment s set s.departureReminderSentAt = :now "
            + "where s.id = :id and s.departureReminderSentAt is null")
    int claimDepartureReminder(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update SeatAssignment s set s.boardingReminderSentAt = :now where s.id = :id and s.boardingReminderSentAt is null")
    int claimBoardingReminder(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("update SeatAssignment s set s.notBoardedNoticeSentAt = :now "
            + "where s.id in :ids and s.notBoardedNoticeSentAt is null")
    int claimNotBoardedNotice(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Query(value = "select id from seat_assignments where id = :id and is_deleted = false for update",
            nativeQuery = true)
    Optional<UUID> lockById(@Param("id") UUID id);

    @Query("""
            select s.id as seatId, s.shuttle.id as shuttleId, s.direction as direction,
                   s.jobAssignment.status as status
            from SeatAssignment s
            where s.jobAssignment.job.id = :jobId and s.jobAssignment.worker.id = :workerId
            """)
    List<WorkerSeat> findWorkerSeatsOnJob(@Param("jobId") UUID jobId, @Param("workerId") UUID workerId);

    @EntityGraph(attributePaths = {"jobAssignment"})
    @Query("""
            select s from SeatAssignment s
            where s.stop.id = :stopId
              and s.notBoardedNoticeSentAt is null
              and s.jobAssignment.status in :statuses
            """)
    List<SeatAssignment> findNotBoardedCandidatesByStopId(@Param("stopId") UUID stopId,
                                                          @Param("statuses") Collection<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"jobAssignment", "stop"})
    @Query("""
            select s from SeatAssignment s
            where s.shuttle.id = :shuttleId
              and s.jobAssignment.status in :statuses
            order by s.stop.sortOrder asc, s.createdAt asc, s.id asc
            """)
    List<SeatAssignment> findBoardingRowsByShuttleId(@Param("shuttleId") UUID shuttleId,
                                                     @Param("statuses") Collection<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job",
            "shuttle", "stop", "stop.address", "stop.address.district"})
    Optional<SeatAssignment> findWithDetailsById(UUID id);

    interface WorkerSeat {
        UUID getSeatId();

        UUID getShuttleId();

        TransportDirection getDirection();

        AssignmentStatus getStatus();
    }

    interface ShuttleSeatCount {
        UUID getShuttleId();

        long getTotal();
    }

    interface StopSeatCount {
        UUID getStopId();

        long getTotal();
    }
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.SeatAssignment;
import com.exskylab.koala.entities.TransportDirection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job",
            "shuttle", "stop", "stop.address", "stop.address.district"})
    Optional<SeatAssignment> findWithDetailsById(UUID id);

    interface ShuttleSeatCount {
        UUID getShuttleId();

        long getTotal();
    }

    interface StopSeatCount {
        UUID getStopId();

        long getTotal();
    }
}

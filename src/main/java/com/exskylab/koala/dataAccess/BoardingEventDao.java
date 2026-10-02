package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.BoardingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardingEventDao extends JpaRepository<BoardingEvent, UUID> {

    Optional<BoardingEvent> findBySeatAssignmentIdAndClientEventId(UUID seatAssignmentId, UUID clientEventId);

    List<BoardingEvent> findAllBySeatAssignmentIdAndBoardingDayOrderByCreatedAtAscIdAsc(UUID seatAssignmentId,
                                                                                       LocalDate boardingDay);

    List<BoardingEvent> findAllBySeatAssignmentIdInOrderByCreatedAtAscIdAsc(Collection<UUID> seatAssignmentIds);

    List<BoardingEvent> findAllByJobAssignmentIdOrderByOccurredAtAscIdAsc(UUID jobAssignmentId);
}

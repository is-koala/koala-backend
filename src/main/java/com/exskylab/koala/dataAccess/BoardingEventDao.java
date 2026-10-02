package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.BoardingEvent;
import com.exskylab.koala.entities.BoardingKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardingEventDao extends JpaRepository<BoardingEvent, UUID> {

    Optional<BoardingEvent> findByClientEventId(UUID clientEventId);

    Optional<BoardingEvent> findFirstBySeatAssignmentIdAndKindAndBoardingDayOrderByOccurredAtAsc(
            UUID seatAssignmentId, BoardingKind kind, LocalDate boardingDay);

    List<BoardingEvent> findAllBySeatAssignmentIdInOrderByOccurredAtDescIdDesc(Collection<UUID> seatAssignmentIds);

    List<BoardingEvent> findAllBySeatAssignment_JobAssignment_IdOrderByOccurredAtAsc(UUID jobAssignmentId);
}

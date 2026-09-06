package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.WorkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkSessionDao extends JpaRepository<WorkSession, UUID> {

    Optional<WorkSession> findByJobAssignmentIdAndCheckOutAtIsNull(UUID jobAssignmentId);

    Optional<WorkSession> findByJobAssignmentIdAndCheckInClientEventId(UUID jobAssignmentId, UUID clientEventId);

    Optional<WorkSession> findByJobAssignmentIdAndCheckOutClientEventId(UUID jobAssignmentId, UUID clientEventId);

    List<WorkSession> findAllByJobAssignmentIdOrderByCheckInAtAsc(UUID jobAssignmentId);

    @Query(value = "select true from pg_advisory_xact_lock(hashtextextended(cast(:assignmentId as text), 0))",
            nativeQuery = true)
    boolean lockAssignment(UUID assignmentId);
}

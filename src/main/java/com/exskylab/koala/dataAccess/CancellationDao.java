package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Cancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CancellationDao extends JpaRepository<Cancellation, UUID> {

    List<Cancellation> findAllByJobAssignmentIdIn(Collection<UUID> jobAssignmentIds);

    @EntityGraph(attributePaths = "refund")
    Optional<Cancellation> findByJobAssignmentId(UUID jobAssignmentId);

    @EntityGraph(attributePaths = {"refund", "cancelledBy", "cancelledBy.profilePicture"})
    @Query("select c from Cancellation c where c.jobAssignment.id = :jobAssignmentId")
    Optional<Cancellation> findWithCancellerByJobAssignmentId(@Param("jobAssignmentId") UUID jobAssignmentId);

    List<Cancellation> findAllByRefundIdIn(Collection<UUID> refundIds);

    boolean existsByJobAssignmentId(UUID jobAssignmentId);
}

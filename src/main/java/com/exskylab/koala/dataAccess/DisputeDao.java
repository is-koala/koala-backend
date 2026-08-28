package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeDao extends JpaRepository<Dispute, UUID> {

    Optional<Dispute> findByJobAssignmentIdAndStatusIn(UUID jobAssignmentId, List<DisputeStatus> statuses);

    boolean existsByJobAssignmentIdAndStatusIn(UUID jobAssignmentId, List<DisputeStatus> statuses);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    Page<Dispute> findAllByStatusIn(List<DisputeStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    List<Dispute> findAllByJobAssignmentIdOrderByCreatedAtDesc(UUID jobAssignmentId);
}

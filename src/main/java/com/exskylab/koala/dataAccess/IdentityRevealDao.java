package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IdentityReveal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface IdentityRevealDao extends JpaRepository<IdentityReveal, UUID> {

    boolean existsByViewer_IdAndJobAssignment_IdAndRevealedAtAfter(UUID viewerId, UUID jobAssignmentId,
                                                                    Instant after);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.job"})
    Page<IdentityReveal> findAllByWorker_Id(UUID workerId, Pageable pageable);
}

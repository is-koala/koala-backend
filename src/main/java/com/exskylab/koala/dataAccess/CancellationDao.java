package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Cancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CancellationDao extends JpaRepository<Cancellation, UUID> {

    Optional<Cancellation> findByJobAssignmentId(UUID jobAssignmentId);

    boolean existsByJobAssignmentId(UUID jobAssignmentId);
}

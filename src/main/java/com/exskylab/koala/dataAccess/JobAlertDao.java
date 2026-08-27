package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobAlert;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobAlertDao extends JpaRepository<JobAlert, UUID> {

    @EntityGraph(attributePaths = {"category"})
    List<JobAlert> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"category", "user"})
    Optional<JobAlert> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"category", "user"})
    List<JobAlert> findAllByActiveTrue();
}

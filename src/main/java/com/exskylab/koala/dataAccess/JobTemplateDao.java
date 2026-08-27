package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobTemplateDao extends JpaRepository<JobTemplate, UUID> {

    @EntityGraph(attributePaths = {"category", "company"})
    List<JobTemplate> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @EntityGraph(attributePaths = {"category", "company", "owner"})
    Optional<JobTemplate> findWithDetailsById(UUID id);
}

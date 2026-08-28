package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobImageDao extends JpaRepository<JobImage, UUID> {

    @EntityGraph(attributePaths = {"image"})
    List<JobImage> findAllByJobIdOrderBySortOrderAsc(UUID jobId);

    @EntityGraph(attributePaths = {"image"})
    List<JobImage> findAllByJobIdInAndCoverTrue(Collection<UUID> jobIds);

    @EntityGraph(attributePaths = {"image", "job"})
    Optional<JobImage> findWithDetailsById(UUID id);

    long countByJobId(UUID jobId);

    List<JobImage> findAllByImageId(UUID imageId);
}

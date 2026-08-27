package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobApplicationDao extends JpaRepository<JobApplication, UUID> {

    boolean existsByJobIdAndUserId(UUID jobId, UUID userId);

    List<JobApplication> findAllByJobId(UUID jobId);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "assignment"})
    Page<JobApplication> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "assignment"})
    Page<JobApplication> findAllByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);
}

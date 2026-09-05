package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobApplicationDao extends JpaRepository<JobApplication, UUID> {

    boolean existsByJobIdAndUserId(UUID jobId, UUID userId);

    boolean existsByJobIdAndUserIdAndStatusNot(UUID jobId, UUID userId, ApplicationStatus status);

    boolean existsByUserIdAndJobCreatorId(UUID userId, UUID creatorId);

    @Query("select a.job.id as jobId, count(a) as total from JobApplication a "
            + "where a.job.id in :jobIds group by a.job.id")
    List<JobApplicationCount> countByJobIds(@Param("jobIds") Collection<UUID> jobIds);

    interface JobApplicationCount {
        UUID getJobId();

        long getTotal();
    }

    @Query("select distinct a.job.company.id from JobApplication a "
            + "where a.user.id = :workerId and a.job.company is not null")
    List<UUID> findCompanyIdsAppliedToBy(@Param("workerId") UUID workerId);

    @EntityGraph(attributePaths = {"user", "user.profilePicture", "user.cv", "assignment"})
    List<JobApplication> findAllByJobId(UUID jobId);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "assignment"})
    Page<JobApplication> findAllByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.company", "assignment"})
    Page<JobApplication> findAllByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);
}

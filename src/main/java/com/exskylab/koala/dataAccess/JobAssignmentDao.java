package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobAssignmentDao extends JpaRepository<JobAssignment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JobAssignment a where a.id = :id")
    Optional<JobAssignment> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {
            "worker",
            "worker.profilePicture",
            "job",
            "job.creator",
            "job.creator.profilePicture",
            "job.company",
            "job.company.logo",
            "job.address",
            "job.creator.address",
            "application"
    })
    Optional<JobAssignment> findWithDetailsForPaymentById(UUID id);

    @Query("select count(a) > 0 from JobAssignment a where "
            + "(a.worker.id = :userId or a.job.creator.id = :userId) and a.paymentStatus = :paymentStatus")
    boolean existsMoneyForUser(@Param("userId") UUID userId,
                               @Param("paymentStatus") PaymentStatus paymentStatus);

    @Query("select count(a) > 0 from JobAssignment a where "
            + "(a.worker.id = :userId or a.job.creator.id = :userId) and a.status in :statuses")
    boolean existsActiveForUser(@Param("userId") UUID userId,
                                @Param("statuses") java.util.List<AssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.address.city", "job.address.district",
            "job.creator", "job.creator.profilePicture", "job.company", "job.company.logo",
            "worker", "worker.profilePicture", "application"})
    Page<JobAssignment> findAllByWorkerId(UUID workerId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.address.city", "job.address.district",
            "job.creator", "job.creator.profilePicture", "job.company", "job.company.logo",
            "worker", "worker.profilePicture", "application"})
    Page<JobAssignment> findAllByJobCreatorId(UUID creatorId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.category", "job.address", "job.address.city", "job.address.district",
            "job.creator", "job.creator.profilePicture", "job.company", "job.company.logo",
            "worker", "worker.profilePicture", "application"})
    Page<JobAssignment> findAllByJobId(UUID jobId, Pageable pageable);

    @Query("""
            select assignment from JobAssignment assignment
            left join assignment.worker worker
            left join assignment.job job
            left join job.creator creator
            where cast(:query as string) is null
               or cast(assignment.id as string) like lower(concat('%', cast(:query as string), '%'))
               or unaccent(lower(job.title)) like unaccent(lower(concat('%', cast(:query as string), '%')))
               or unaccent(lower(worker.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
               or unaccent(lower(concat(worker.firstName, ' ', worker.lastName)))
                  like unaccent(lower(concat('%', cast(:query as string), '%')))
               or unaccent(lower(creator.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
               or unaccent(lower(concat(creator.firstName, ' ', creator.lastName)))
                  like unaccent(lower(concat('%', cast(:query as string), '%')))
            """)
    @EntityGraph(attributePaths = {
            "worker",
            "worker.profilePicture",
            "job",
            "job.creator",
            "job.creator.profilePicture",
            "job.company"
    })
    Page<JobAssignment> findAllForStaff(@Param("query") String query, Pageable pageable);

    long countByWorkerIdAndStatus(UUID workerId, AssignmentStatus status);

    long countByWorkerId(UUID workerId);

    long countByJobCreatorId(UUID creatorId);

    boolean existsByJobIdAndStatusIn(UUID jobId, java.util.Collection<AssignmentStatus> statuses);

    boolean existsByJobIdAndWorkerIdAndStatusIn(UUID jobId, UUID workerId,
                                                 java.util.Collection<AssignmentStatus> statuses);

    @Query("select distinct a.job.id from JobAssignment a where a.job.id in :jobIds "
            + "and a.worker.id = :workerId and a.status in :statuses")
    List<UUID> findVisibleAddressJobIds(@Param("jobIds") java.util.Collection<UUID> jobIds,
                                        @Param("workerId") UUID workerId,
                                        @Param("statuses") java.util.Collection<AssignmentStatus> statuses);

    long countByJobCreatorIdAndStatus(UUID creatorId, AssignmentStatus status);

    @Query("""
            select a.id from JobAssignment a
            where a.status in :statuses
              and a.paymentStatus = :paymentStatus
              and a.job.endDateTime is not null
              and a.job.endDateTime <= :endedBefore
            order by a.job.endDateTime asc
            """)
    List<UUID> findAutoCompletableIds(@Param("statuses") List<AssignmentStatus> statuses,
                                      @Param("paymentStatus") PaymentStatus paymentStatus,
                                      @Param("endedBefore") java.time.LocalDateTime endedBefore,
                                      Pageable pageable);

    @Query("""
            select a.id from JobAssignment a
            where a.status = :status
              and (a.paymentStatus is null or a.paymentStatus in :paymentStatuses)
              and a.job.startDateTime is not null
              and a.job.startDateTime <= :startingBefore
            order by a.job.startDateTime asc
            """)
    List<UUID> findUnpaidExpiringIds(@Param("status") AssignmentStatus status,
                                     @Param("paymentStatuses") List<PaymentStatus> paymentStatuses,
                                     @Param("startingBefore") java.time.LocalDateTime startingBefore,
                                     Pageable pageable);

    @Query("select a.job.id as jobId, count(a) as total from JobAssignment a "
            + "where a.job.id in :jobIds and a.status in :statuses group by a.job.id")
    List<HiredCount> countHiredByJobIds(@Param("jobIds") java.util.Collection<UUID> jobIds,
                                        @Param("statuses") java.util.Collection<AssignmentStatus> statuses);

    long countByJobIdAndStatusIn(UUID jobId, java.util.Collection<AssignmentStatus> statuses);

    long countByJobId(UUID jobId);

    List<JobAssignment> findAllByJobIdAndWorkerId(UUID jobId, UUID workerId);

    List<JobAssignment> findAllByJobIdInAndWorkerIdIn(java.util.Collection<UUID> jobIds,
                                                      java.util.Collection<UUID> workerIds);

    interface HiredCount {
        UUID getJobId();

        long getTotal();
    }
}

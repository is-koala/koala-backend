package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.DisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeDao extends JpaRepository<Dispute, UUID> {

    @Query("select d.jobAssignment.id from Dispute d where d.id = :id")
    Optional<UUID> findJobAssignmentIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForUpdate(@Param("id") UUID id);

    Optional<Dispute> findByJobAssignmentIdAndStatusIn(UUID jobAssignmentId, List<DisputeStatus> statuses);

    boolean existsByJobAssignmentIdAndStatusIn(UUID jobAssignmentId, List<DisputeStatus> statuses);

    @Query("""
            select (count(d) > 0) from Dispute d
            where d.status in :statuses
              and (d.jobAssignment.worker.id = :userId or d.jobAssignment.job.creator.id = :userId)
            """)
    boolean existsActiveForUser(@Param("userId") UUID userId,
                                @Param("statuses") List<DisputeStatus> statuses);

    @Query("""
            select count(d) from Dispute d
            where d.jobAssignment.worker.id = :userId or d.jobAssignment.job.creator.id = :userId
            """)
    long countInvolving(@Param("userId") UUID userId);

    @Query("""
            select d from Dispute d
            left join d.jobAssignment assignment
            left join assignment.worker worker
            left join assignment.job job
            left join job.creator creator
            left join d.openedBy opener
            where d.status in :statuses
              and (cast(:query as string) is null
                   or cast(d.id as string) like lower(concat('%', cast(:query as string), '%'))
                   or cast(assignment.id as string) like lower(concat('%', cast(:query as string), '%'))
                   or unaccent(lower(worker.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(worker.firstName, ' ', worker.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(creator.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(creator.firstName, ' ', creator.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(opener.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(opener.firstName, ' ', opener.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    Page<Dispute> searchForStaff(@Param("statuses") List<DisputeStatus> statuses, @Param("query") String query,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForStaff(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    List<Dispute> findAllByJobAssignmentIdOrderByCreatedAtDesc(UUID jobAssignmentId);
}

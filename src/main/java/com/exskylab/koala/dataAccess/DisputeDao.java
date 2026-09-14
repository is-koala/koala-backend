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

import java.time.Instant;
import java.util.Collection;
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
            select count(d) from Dispute d
            where d.jobAssignment.worker.id = :userId
               or d.jobAssignment.job.creator.id = :userId
               or d.openedBy.id = :userId
            """)
    long countInvolving(@Param("userId") UUID userId);

    @Query("""
            select d.id as id, a.worker.id as workerId, a.job.creator.id as employerId
            from Dispute d join d.jobAssignment a
            where d.status in :statuses
              and (a.worker.id in :userIds or a.job.creator.id in :userIds)
            """)
    List<PartyLink> findActiveLinksForUsers(@Param("userIds") Collection<UUID> userIds,
                                            @Param("statuses") List<DisputeStatus> statuses);

    @Query("""
            select d from Dispute d
            left join d.jobAssignment assignment
            left join assignment.worker worker
            left join assignment.job job
            left join job.creator creator
            left join d.openedBy opener
            where d.status in :statuses
              and d.createdAt >= :from
              and d.createdAt <= :to
              and (:userId is null or worker.id = :userId or creator.id = :userId or opener.id = :userId)
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
                                 @Param("userId") UUID userId, @Param("from") Instant from,
                                 @Param("to") Instant to, Pageable pageable);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> findByIdForStaff(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"openedBy", "openedBy.profilePicture", "jobAssignment"})
    List<Dispute> findAllByJobAssignmentIdOrderByCreatedAtDesc(UUID jobAssignmentId);

    List<Dispute> findAllByJobAssignmentIdInOrderByCreatedAtDesc(Collection<UUID> jobAssignmentIds);
}

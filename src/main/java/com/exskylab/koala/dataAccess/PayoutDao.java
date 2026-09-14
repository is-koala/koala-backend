package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutDao extends JpaRepository<Payout, UUID> {

    List<Payout> findAllByJobAssignmentIdIn(Collection<UUID> jobAssignmentIds);

    Optional<Payout> findByPaymentId(UUID paymentId);

    @EntityGraph(attributePaths = {"bankAccount", "payment"})
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findForStaffById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.job", "jobAssignment.job.company", "worker",
            "bankAccount"})
    List<Payout> findAllByWorkerIdOrderByCreatedAtDesc(UUID workerId);

    boolean existsByJobAssignmentId(UUID jobAssignmentId);

    long countByWorkerId(UUID workerId);

    Optional<Payout> findByJobAssignmentId(UUID jobAssignmentId);

    @Query("""
            select p from Payout p
            left join p.worker worker
            left join p.jobAssignment assignment
            where p.status in :statuses
              and p.createdAt >= :from
              and p.createdAt <= :to
              and (:workerId is null or worker.id = :workerId)
              and (cast(:query as string) is null
                   or cast(p.id as string) like lower(concat('%', cast(:query as string), '%'))
                   or cast(assignment.id as string) like lower(concat('%', cast(:query as string), '%'))
                   or unaccent(lower(worker.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(worker.firstName, ' ', worker.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    @EntityGraph(attributePaths = {"bankAccount"})
    Page<Payout> searchForStaff(@Param("statuses") List<PayoutStatus> statuses, @Param("query") String query,
                                @Param("workerId") UUID workerId, @Param("from") Instant from,
                                @Param("to") Instant to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

    @Query("select p.id from Payout p where p.status in :statuses and p.settlementReference is null "
            + "and p.processingAt < :threshold order by p.processingAt")
    List<UUID> findIdsAwaitingSettlementSince(@Param("statuses") List<PayoutStatus> statuses,
                                              @Param("threshold") Instant threshold);

    @Query("""
            select coalesce(sum(p.amount), 0), count(p)
            from Payout p
            where p.status = :status
              and p.currency = :currency
              and p.completedAt >= :fromInclusive
              and p.completedAt < :toExclusive
            """)
    List<Object[]> summarizeCompleted(@Param("status") PayoutStatus status,
                                      @Param("currency") String currency,
                                      @Param("fromInclusive") Instant fromInclusive,
                                      @Param("toExclusive") Instant toExclusive);

    @Query("""
            select coalesce(sum(p.amount), 0), count(p)
            from Payout p
            where p.status in :statuses
              and p.currency = :currency
            """)
    List<Object[]> summarizeAwaiting(@Param("statuses") List<PayoutStatus> statuses,
                                     @Param("currency") String currency);
}

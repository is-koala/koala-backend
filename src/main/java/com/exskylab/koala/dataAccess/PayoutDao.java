package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.entities.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutDao extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByPaymentId(UUID paymentId);

    @EntityGraph(attributePaths = {"jobAssignment", "worker", "bankAccount"})
    List<Payout> findAllByWorkerIdOrderByCreatedAtDesc(UUID workerId);

    boolean existsByJobAssignmentId(UUID jobAssignmentId);

    Optional<Payout> findByJobAssignmentId(UUID jobAssignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(p) from Payout p where p.status in :statuses and p.createdAt < :threshold")
    long countAwaitingSettlementSince(@Param("statuses") List<PayoutStatus> statuses,
                                      @Param("threshold") Instant threshold);
}

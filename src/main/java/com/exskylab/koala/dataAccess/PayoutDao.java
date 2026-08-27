package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Payout;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutDao extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByPaymentId(UUID paymentId);

    @EntityGraph(attributePaths = {"jobAssignment", "worker", "bankAccount"})
    List<Payout> findAllByWorkerIdOrderByCreatedAtDesc(UUID workerId);

    boolean existsByJobAssignmentId(UUID jobAssignmentId);

    java.util.Optional<Payout> findByJobAssignmentId(UUID jobAssignmentId);
}

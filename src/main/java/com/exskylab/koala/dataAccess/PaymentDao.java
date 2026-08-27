package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentDao extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job"})
    Optional<Payment> findByProviderToken(String providerToken);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job"})
    Optional<Payment> findByConversationId(String conversationId);

    List<Payment> findAllByJobAssignmentIdOrderByCreatedAtDesc(UUID jobAssignmentId);

    boolean existsByJobAssignmentIdAndStatus(UUID jobAssignmentId, PaymentAttemptStatus status);
}

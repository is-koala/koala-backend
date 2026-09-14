package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentDao extends JpaRepository<Payment, UUID> {

    @Query("""
            select p.jobAssignment.id as jobAssignmentId, min(p.completedAt) as securedAt
            from Payment p
            where p.jobAssignment.id in :jobAssignmentIds
              and p.status = :status
              and p.completedAt is not null
            group by p.jobAssignment.id
            """)
    List<SecuredAtView> findSecuredAtByJobAssignmentIds(@Param("jobAssignmentIds") Collection<UUID> jobAssignmentIds,
                                                        @Param("status") PaymentAttemptStatus status);

    interface SecuredAtView {
        UUID getJobAssignmentId();

        Instant getSecuredAt();
    }

    @Query("select p.jobAssignment.id from Payment p where p.providerToken = :providerToken")
    Optional<UUID> findJobAssignmentIdByProviderToken(@Param("providerToken") String providerToken);

    @Query("select p.jobAssignment.id from Payment p where p.id = :id")
    Optional<UUID> findJobAssignmentIdById(@Param("id") UUID id);

    @Query("""
            select p.status as status, p.jobAssignment.id as jobAssignmentId
            from Payment p
            where p.providerToken = :providerToken
            """)
    Optional<RecordedOutcomeView> findRecordedOutcomeByProviderToken(@Param("providerToken") String providerToken);

    interface RecordedOutcomeView {
        PaymentAttemptStatus getStatus();

        UUID getJobAssignmentId();
    }

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByProviderToken(String providerToken);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithDetailsById(@Param("id") UUID id);

    @Query("""
            select p.id from Payment p
            where p.status in :statuses
              and p.nextVerificationAt is not null
              and p.nextVerificationAt <= :now
            order by p.nextVerificationAt asc
            """)
    List<UUID> findDueVerificationIds(@Param("statuses") List<PaymentAttemptStatus> statuses,
                                      @Param("now") Instant now, Pageable pageable);

    @Query("""
            select p.id from Payment p
            where p.status = :status
              and p.providerToken is null
              and p.createdAt <= :cutoff
            order by p.createdAt asc
            """)
    List<UUID> findStuckInitiationIds(@Param("status") PaymentAttemptStatus status,
                                      @Param("cutoff") Instant cutoff, Pageable pageable);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job"})
    Optional<Payment> findByConversationId(String conversationId);

    List<Payment> findAllByJobAssignmentIdOrderByCreatedAtDesc(UUID jobAssignmentId);

    long countByJobAssignmentJobCreatorId(UUID creatorId);

    @EntityGraph(attributePaths = {"jobAssignment", "jobAssignment.worker", "jobAssignment.job",
            "jobAssignment.job.creator", "jobAssignment.job.company"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForStaffById(@Param("id") UUID id);

    boolean existsByJobAssignmentIdAndStatus(UUID jobAssignmentId, PaymentAttemptStatus status);

    boolean existsByJobAssignmentIdAndStatusIn(UUID jobAssignmentId, List<PaymentAttemptStatus> statuses);

    @Query("""
            select p.id as id, a.worker.id as workerId, a.job.creator.id as employerId
            from Payment p join p.jobAssignment a
            where p.status in :statuses
              and (a.worker.id in :userIds or a.job.creator.id in :userIds)
            """)
    List<PartyLink> findPendingLinksForUsers(@Param("userIds") Collection<UUID> userIds,
                                             @Param("statuses") List<PaymentAttemptStatus> statuses);

    @Query("""
            select coalesce(sum(p.totalCharged), 0),
                   coalesce(sum(p.commissionAmount), 0),
                   coalesce(sum(p.workerPayout), 0),
                   count(p)
            from Payment p
            where p.status = :status
              and p.currency = :currency
              and p.completedAt >= :fromInclusive
              and p.completedAt < :toExclusive
            """)
    List<Object[]> summarizeSucceeded(@Param("status") PaymentAttemptStatus status,
                                      @Param("currency") String currency,
                                      @Param("fromInclusive") Instant fromInclusive,
                                      @Param("toExclusive") Instant toExclusive);
}

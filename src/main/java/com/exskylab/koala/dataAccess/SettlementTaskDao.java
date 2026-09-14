package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.SettlementOperation;
import com.exskylab.koala.entities.SettlementTask;
import com.exskylab.koala.entities.SettlementTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementTaskDao extends JpaRepository<SettlementTask, UUID> {

    Optional<SettlementTask> findByPaymentIdAndOperation(UUID paymentId, SettlementOperation operation);

    List<SettlementTask> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    @Query("""
            select t.id from SettlementTask t
            where t.status = :status and t.nextAttemptAt <= :dueBefore
            order by t.nextAttemptAt asc
            """)
    List<UUID> findDueIds(@Param("status") SettlementTaskStatus status,
                          @Param("dueBefore") Instant dueBefore,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"payment", "jobAssignment"})
    Optional<SettlementTask> findWithDetailsById(UUID id);
}

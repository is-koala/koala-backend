package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AccountDeletionRequest;
import com.exskylab.koala.entities.AccountDeletionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountDeletionRequestDao extends JpaRepository<AccountDeletionRequest, UUID> {

    Optional<AccountDeletionRequest> findFirstByUserIdAndStatusOrderByRequestedAtDesc(
            UUID userId, AccountDeletionStatus status);

    Optional<AccountDeletionRequest> findFirstByUserIdOrderByRequestedAtDesc(UUID userId);

    @Query("""
            select r from AccountDeletionRequest r
            left join r.user u
            where (:status is null or r.status = :status)
              and (cast(:query as string) is null
                   or unaccent(lower(u.email)) like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    Page<AccountDeletionRequest> search(@Param("status") AccountDeletionStatus status,
                                        @Param("query") String query, Pageable pageable);

    @Query("""
            select r.id from AccountDeletionRequest r
            where r.status = :status and r.requestedAt <= :dueBefore
              and (r.nextAttemptAt is null or r.nextAttemptAt <= :attemptBefore)
            order by case when r.nextAttemptAt is null then 0 else 1 end,
                     r.nextAttemptAt asc, r.requestedAt asc
            """)
    List<UUID> findDueIds(@Param("status") AccountDeletionStatus status,
                          @Param("dueBefore") Instant dueBefore,
                          @Param("attemptBefore") Instant attemptBefore, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select r from AccountDeletionRequest r where r.id = :id")
    Optional<AccountDeletionRequest> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "user")
    @Override
    Optional<AccountDeletionRequest> findById(UUID id);
}

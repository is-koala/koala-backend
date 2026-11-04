package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IdentityVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityVerificationDao extends JpaRepository<IdentityVerification, UUID> {

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument", "selfieDocument", "togetherDocument"})
    @Override
    Optional<IdentityVerification> findById(UUID id);

    @Query(value = "select user_id from user_verifications where id = :id and verification_type = 'IDENTITY'",
            nativeQuery = true)
    Optional<UUID> findUserIdIncludingDeletedById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from IdentityVerification v where v.id = :id")
    Optional<IdentityVerification> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select v from IdentityVerification v
            where v.user.id = :userId and v.verified = false and v.rejectedAt is null
            """)
    Optional<IdentityVerification> findPendingByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument", "selfieDocument", "togetherDocument"})
    Optional<IdentityVerification> findTopByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query(value = """
            select exists(select 1 from media
                          where media_type = 'VERIFICATION_DOCUMENT' and file_url = :storageKey)
            """, nativeQuery = true)
    boolean existsDocumentWithStorageKey(@Param("storageKey") String storageKey);

    @Query("""
            select v from IdentityVerification v
            left join v.user u
            where ((cast(:status as string) = 'PENDING' and v.verified = false and v.rejectedAt is null)
                   or (cast(:status as string) = 'APPROVED' and v.verified = true)
                   or (cast(:status as string) = 'REJECTED' and v.rejectedAt is not null))
              and (cast(:query as string) is null
                   or unaccent(lower(u.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(u.firstName, ' ', u.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(v.firstName, ' ', v.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument", "selfieDocument", "togetherDocument"})
    Page<IdentityVerification> search(@Param("status") String status, @Param("query") String query,
                                      Pageable pageable);
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IdentityVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityVerificationDao extends JpaRepository<IdentityVerification, UUID> {

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument"})
    @Override
    Optional<IdentityVerification> findById(UUID id);

    @Query("""
            select v from IdentityVerification v
            where v.user.id = :userId and v.verified = false and v.rejectedAt is null
            """)
    Optional<IdentityVerification> findPendingByUserId(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument"})
    Optional<IdentityVerification> findTopByUser_IdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument"})
    Page<IdentityVerification> findByVerifiedFalseAndRejectedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument"})
    Page<IdentityVerification> findByVerifiedTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "verificationDocument", "backDocument"})
    Page<IdentityVerification> findByRejectedAtIsNotNull(Pageable pageable);
}

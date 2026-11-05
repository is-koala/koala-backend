package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.BankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankAccountDao extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByUserIdAndPrimaryAccountTrueAndActiveTrue(UUID userId);

    List<BankAccount> findAllByUserIdAndActiveTrue(UUID userId);

    List<BankAccount> findAllByUserIdAndActiveTrueOrderByPrimaryAccountDescCreatedAtAsc(UUID userId);

    boolean existsByUserIdAndActiveTrue(UUID userId);

    Optional<BankAccount> findByIdAndUserIdAndActiveTrue(UUID accountId, UUID userId);

    Optional<BankAccount> findByUserIdAndIbanAndActiveTrue(UUID userId, String iban);

    Optional<BankAccount> findByVerificationReferenceAndActiveTrue(String verificationReference);

    @Query(value = "select user_id from bank_accounts where verification_reference = :reference",
            nativeQuery = true)
    Optional<UUID> findOwnerIdIncludingInactiveByVerificationReference(@Param("reference") String reference);

    @Query("""
            select account from BankAccount account
            left join account.user owner
            where account.active = true
              and account.verificationStatus = com.exskylab.koala.entities.BankAccountVerificationStatus.PENDING
              and account.verificationReference is not null
              and (account.verificationExpiresAt is null or account.verificationExpiresAt > :now)
              and (:userId is null or owner.id = :userId)
              and (cast(:query as string) is null
                   or unaccent(lower(account.verificationReference))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(owner.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(owner.firstName, ' ', owner.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    @EntityGraph(attributePaths = {"user", "user.profilePicture"})
    Page<BankAccount> findAwaitingStaffConfirmation(@Param("now") Instant now, @Param("query") String query,
                                                    @Param("userId") UUID userId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BankAccount account set account.primaryAccount = false "
            + "where account.user.id = :userId and account.primaryAccount = true and account.active = true")
    int clearPrimary(UUID userId);

    @Query(value = "select true from pg_advisory_xact_lock(hashtextextended(cast(:userId as text), 0))",
            nativeQuery = true)
    boolean lockUserAccounts(UUID userId);
}

package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenDao extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select token.user.id from PasswordResetToken token where token.tokenDigest = :tokenDigest")
    Optional<UUID> findUserIdByTokenDigest(@Param("tokenDigest") String tokenDigest);

    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
               set token.consumedAt = :now
             where token.tokenDigest = :tokenDigest
               and token.consumedAt is null
               and token.revokedAt is null
               and token.expiresAt > :now
               and token.deleted = false
            """)
    int consume(@Param("tokenDigest") String tokenDigest, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
               set token.revokedAt = :now
             where token.user.id = :userId
               and token.consumedAt is null
               and token.revokedAt is null
               and token.deleted = false
            """)
    int revokeActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}

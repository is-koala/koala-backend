package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.IdentityChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityChallengeDao extends JpaRepository<IdentityChallenge, UUID> {

    Optional<IdentityChallenge> findByUserIdAndChallengeAndConsumedAtIsNull(UUID userId, String challenge);
}

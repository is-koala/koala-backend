package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.UnsubscribeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnsubscribeTokenDao extends JpaRepository<UnsubscribeToken, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<UnsubscribeToken> findByTokenDigest(String tokenDigest);
}

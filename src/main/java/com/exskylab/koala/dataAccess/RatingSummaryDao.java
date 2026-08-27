package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.RatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RatingSummaryDao extends JpaRepository<RatingSummary, UUID> {

    Optional<RatingSummary> findByUserId(UUID userId);
}

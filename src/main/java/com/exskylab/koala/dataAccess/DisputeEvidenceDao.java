package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.DisputeEvidence;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeEvidenceDao extends JpaRepository<DisputeEvidence, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "uploadedBy.profilePicture"})
    List<DisputeEvidence> findAllByDisputeIdOrderByCreatedAtAsc(UUID disputeId);

    Optional<DisputeEvidence> findByIdAndDisputeId(UUID id, UUID disputeId);

    long countByDisputeId(UUID disputeId);

    String SCRUB_CANDIDATES = """
            from DisputeEvidence e
             where e.metadataScrubbedAt is null
               and e.storageKey is not null
               and e.contentType in :contentTypes
               and (:retryFailed = true or e.metadataScrubError is null)
            """;

    @Query("select e.id " + SCRUB_CANDIDATES + " order by e.createdAt asc, e.id asc")
    List<UUID> findScrubCandidateIds(@Param("contentTypes") Collection<String> contentTypes,
                                     @Param("retryFailed") boolean retryFailed, Pageable page);

    @Query("select count(e) " + SCRUB_CANDIDATES)
    long countScrubCandidates(@Param("contentTypes") Collection<String> contentTypes,
                              @Param("retryFailed") boolean retryFailed);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DisputeEvidence e where e.id = :id")
    Optional<DisputeEvidence> findByIdForUpdate(@Param("id") UUID id);
}

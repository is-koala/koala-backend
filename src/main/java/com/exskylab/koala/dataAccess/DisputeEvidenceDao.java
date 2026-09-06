package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.DisputeEvidence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeEvidenceDao extends JpaRepository<DisputeEvidence, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy", "uploadedBy.profilePicture"})
    List<DisputeEvidence> findAllByDisputeIdOrderByCreatedAtAsc(UUID disputeId);

    Optional<DisputeEvidence> findByIdAndDisputeId(UUID id, UUID disputeId);

    long countByDisputeId(UUID disputeId);
}

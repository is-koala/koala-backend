package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.ConsentRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentRecordDao extends JpaRepository<ConsentRecord, UUID> {

    @EntityGraph(attributePaths = {"agreementVersion"})
    List<ConsentRecord> findAllByUserIdOrderByRecordedAtDesc(UUID userId);

    boolean existsByUserIdAndAgreementVersionIdAndGrantedTrueAndRevokedAtIsNull(
            UUID userId, UUID agreementVersionId);
}

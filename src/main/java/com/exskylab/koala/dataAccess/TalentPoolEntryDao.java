package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.TalentPoolEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TalentPoolEntryDao extends JpaRepository<TalentPoolEntry, UUID> {

    @EntityGraph(attributePaths = {"worker", "worker.profilePicture", "company"})
    org.springframework.data.domain.Page<TalentPoolEntry> findAllByEmployerId(UUID employerId,
            org.springframework.data.domain.Pageable pageable);

    Optional<TalentPoolEntry> findByEmployerIdAndWorkerId(UUID employerId, UUID workerId);

    boolean existsByEmployerIdAndWorkerId(UUID employerId, UUID workerId);
}

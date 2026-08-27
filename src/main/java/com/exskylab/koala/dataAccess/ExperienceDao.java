package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Experience;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExperienceDao extends JpaRepository<Experience, UUID> {

    List<Experience> findAllByUserIdOrderByStartDateDesc(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Experience> findWithUserById(UUID id);
}

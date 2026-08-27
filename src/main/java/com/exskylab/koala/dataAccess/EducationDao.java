package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Education;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EducationDao extends JpaRepository<Education, UUID> {

    @EntityGraph(attributePaths = {"university", "faculty", "department"})
    List<Education> findAllByUserIdOrderByStartDateDesc(UUID userId);

    @EntityGraph(attributePaths = {"university", "faculty", "department", "user"})
    Optional<Education> findWithDetailsById(UUID id);
}

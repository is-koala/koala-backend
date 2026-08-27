package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacultyDao extends JpaRepository<Faculty, UUID> {

    List<Faculty> findAllByUniversityIdOrderByNameAsc(UUID universityId);
}

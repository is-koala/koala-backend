package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.University;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UniversityDao extends JpaRepository<University, UUID> {

    List<University> findAllByOrderByNameAsc();
}

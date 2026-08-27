package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyDao extends JpaRepository<Company, UUID> {

    Optional<Company> findById(UUID id);

    org.springframework.data.domain.Page<Company> findAllByApproved(boolean approved,
            org.springframework.data.domain.Pageable pageable);
}

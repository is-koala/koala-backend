package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CompanyFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyFollowDao extends JpaRepository<CompanyFollow, UUID> {

    Optional<CompanyFollow> findByCompanyIdAndFollowerId(UUID companyId, UUID followerId);

    boolean existsByCompanyIdAndFollowerId(UUID companyId, UUID followerId);

    long countByCompanyId(UUID companyId);

    @EntityGraph(attributePaths = {"company", "company.logo", "company.address"})
    Page<CompanyFollow> findAllByFollowerId(UUID followerId, Pageable pageable);

    @EntityGraph(attributePaths = {"follower"})
    List<CompanyFollow> findAllByCompanyId(UUID companyId);
}

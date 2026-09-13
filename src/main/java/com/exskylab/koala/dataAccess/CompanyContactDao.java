package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompanyContactDao extends JpaRepository<CompanyContact, UUID> {
    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

    boolean existsByCompanyIdAndUserIdAndRole(UUID companyId, UUID userId, CompanyContactRole companyContactRole);

    @EntityGraph(attributePaths = {"company", "company.logo", "company.address"})
    List<CompanyContact> findAllByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "user.profilePicture"})
    List<CompanyContact> findAllByCompanyId(UUID companyId);

    Optional<CompanyContact> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    long countByCompanyIdAndRole(UUID companyId, CompanyContactRole role);

    @Query("select c.company.id from CompanyContact c where c.user.id = :userId")
    List<UUID> findCompanyIdsByUserId(@Param("userId") UUID userId);
}

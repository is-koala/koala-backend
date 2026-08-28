package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CompanyImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyImageDao extends JpaRepository<CompanyImage, UUID> {

    @EntityGraph(attributePaths = {"image"})
    List<CompanyImage> findAllByCompanyIdOrderBySortOrderAsc(UUID companyId);

    @EntityGraph(attributePaths = {"image", "company"})
    Optional<CompanyImage> findWithDetailsById(UUID id);

    long countByCompanyId(UUID companyId);

    List<CompanyImage> findAllByImageId(UUID imageId);

    Optional<CompanyImage> findByCompanyIdAndImageId(UUID companyId, UUID imageId);
}

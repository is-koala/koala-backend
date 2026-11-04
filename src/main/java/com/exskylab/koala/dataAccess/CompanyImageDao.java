package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.CompanyImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = """
            select company_id from company_images
            where image_id = :imageId
            order by is_deleted, created_at
            limit 1
            """, nativeQuery = true)
    Optional<UUID> findCompanyIdIncludingDeletedByImageId(@Param("imageId") UUID imageId);

    @EntityGraph(attributePaths = {"image"})
    Optional<CompanyImage> findByCompanyIdAndImageId(UUID companyId, UUID imageId);
}

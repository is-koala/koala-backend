package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyDao extends JpaRepository<Company, UUID> {

    @Query(value = "select exists(select 1 from companies where id = :id)", nativeQuery = true)
    boolean existsIncludingDeletedById(@Param("id") UUID id);

    Optional<Company> findById(UUID id);

    @EntityGraph(attributePaths = {
            "createdBy", "createdBy.profilePicture", "logo",
            "address", "address.country", "address.city", "address.district", "address.city.country",
            "images", "images.image"
    })
    @Query("""
            select c from Company c
            where c.approvalStatus = :status
              and (cast(:query as string) is null
                   or lower(c.name) like lower(concat('%', cast(:query as string), '%'))
                   or lower(c.taxNumber) like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<Company> search(@Param("status") CompanyApprovalStatus status,
                         @Param("query") String query,
                         Pageable pageable);
}

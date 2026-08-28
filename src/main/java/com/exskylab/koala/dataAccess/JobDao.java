package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.JobType;
import com.exskylab.koala.entities.SalaryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobDao extends JpaRepository<Job, UUID> {

    @Query("""
            select j.id from Job j
            where j.status = :status
              and (cast(:query as string) is null
                   or lower(j.title) like lower(concat('%', cast(:query as string), '%'))
                   or lower(j.description) like lower(concat('%', cast(:query as string), '%'))
                   or lower(j.position) like lower(concat('%', cast(:query as string), '%')))
              and (:categoryId is null or j.category.id = :categoryId)
              and (:cityId is null or j.address.city.id = :cityId)
              and (:districtId is null or j.address.district.id = :districtId)
              and (:type is null or j.type = :type)
              and (:salaryType is null or j.salaryType = :salaryType)
              and (:employerType is null or j.employerType = :employerType)
              and (:minSalary is null or j.salary >= :minSalary)
              and (:maxSalary is null or j.salary <= :maxSalary)
              and (:insuranceRequired is null or j.insuranceRequired = :insuranceRequired)
              and (:experienceRequired is null or j.experienceRequired = :experienceRequired)
              and (:minLatitude is null or j.address.latitude >= :minLatitude)
              and (:maxLatitude is null or j.address.latitude <= :maxLatitude)
              and (:minLongitude is null or j.address.longitude >= :minLongitude)
              and (:maxLongitude is null or j.address.longitude <= :maxLongitude)
            """)
    Page<UUID> searchIds(@Param("status") JobStatus status,
                         @Param("query") String query,
                         @Param("categoryId") UUID categoryId,
                         @Param("cityId") UUID cityId,
                         @Param("districtId") UUID districtId,
                         @Param("type") JobType type,
                         @Param("salaryType") SalaryType salaryType,
                         @Param("employerType") JobEmployerType employerType,
                         @Param("minSalary") BigDecimal minSalary,
                         @Param("maxSalary") BigDecimal maxSalary,
                         @Param("insuranceRequired") Boolean insuranceRequired,
                         @Param("experienceRequired") Boolean experienceRequired,
                         @Param("minLatitude") Double minLatitude,
                         @Param("maxLatitude") Double maxLatitude,
                         @Param("minLongitude") Double minLongitude,
                         @Param("maxLongitude") Double maxLongitude,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"category", "creator", "company", "address",
            "address.city", "address.district", "address.city.country"})
    List<Job> findAllByIdIn(List<UUID> ids);

    @EntityGraph(attributePaths = {"category", "creator", "company", "address",
            "address.city", "address.district", "address.city.country"})
    Optional<Job> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"category", "company", "address", "address.city", "address.district"})
    Page<Job> findAllByCreatorId(UUID creatorId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "company", "address", "address.city", "address.district"})
    Page<Job> findAllByCompanyId(UUID companyId, Pageable pageable);
}

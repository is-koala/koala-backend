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

    String PUBLIC_LATITUDE = """
            case when j.employerType = com.exskylab.koala.entities.JobEmployerType.COMPANY and j.addressPublic = true
                 then j.address.latitude else round(j.address.latitude, 2) end""";

    String PUBLIC_LONGITUDE = """
            case when j.employerType = com.exskylab.koala.entities.JobEmployerType.COMPANY and j.addressPublic = true
                 then j.address.longitude else round(j.address.longitude, 2) end""";

    String DISTANCE_KM = "case when :located = true and j.address.latitude is not null and j.address.longitude is not null "
            + "then 6371.0 * acos(least(1.0, greatest(-1.0, "
            + "sin(radians(:latitude)) * sin(radians(" + PUBLIC_LATITUDE + ")) "
            + "+ cos(radians(:latitude)) * cos(radians(" + PUBLIC_LATITUDE + ")) "
            + "* cos(radians(" + PUBLIC_LONGITUDE + ") - radians(:longitude))))) else null end";

    interface JobSearchHit {
        UUID getId();

        Double getDistance();
    }

    @Query("select j.id as id, " + DISTANCE_KM + " as distance from Job j " + """
            where j.status = :status
              and (j.endDateTime is null or j.endDateTime > :activeAfter)
              and (:startFiltered = false
                   or (j.startDateTime >= :startFrom and j.startDateTime <= :startTo))
              and (cast(:query as string) is null
                   or lower(j.title) like lower(concat('%', cast(:query as string), '%'))
                   or lower(j.description) like lower(concat('%', cast(:query as string), '%'))
                   or lower(j.position) like lower(concat('%', cast(:query as string), '%')))
              and (:companyId is null or j.company.id = :companyId)
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
    Page<JobSearchHit> searchIds(@Param("status") JobStatus status,
                         @Param("activeAfter") java.time.LocalDateTime activeAfter,
                         @Param("startFiltered") boolean startFiltered,
                         @Param("startFrom") java.time.LocalDateTime startFrom,
                         @Param("startTo") java.time.LocalDateTime startTo,
                         @Param("located") boolean located,
                         @Param("latitude") double latitude,
                         @Param("longitude") double longitude,
                         @Param("query") String query,
                         @Param("companyId") UUID companyId,
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

    @EntityGraph(attributePaths = {"category", "creator", "company", "company.logo", "address",
            "address.city", "address.district", "address.city.country"})
    List<Job> findAllByIdIn(List<UUID> ids);

    @EntityGraph(attributePaths = {"category", "creator", "company", "address",
            "address.city", "address.district", "address.city.country",
            "images", "images.image"})
    Optional<Job> findWithDetailsById(UUID id);

    long countByCreatorId(UUID creatorId);

    @EntityGraph(attributePaths = {"category", "company", "company.logo", "address", "address.city", "address.district"})
    Page<Job> findAllByCreatorId(UUID creatorId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "company", "company.logo", "address", "address.city", "address.district"})
    Page<Job> findAllByCompanyId(UUID companyId, Pageable pageable);

    @Query("""
            select j from Job j
            left join j.creator creator
            left join j.company company
            where j.status in :statuses
              and (:companyId is null or company.id = :companyId)
              and (:employerId is null or creator.id = :employerId)
              and (cast(:query as string) is null
                   or cast(j.id as string) like lower(concat('%', cast(:query as string), '%'))
                   or unaccent(lower(j.title)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(company.name)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(creator.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(creator.firstName, ' ', creator.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """)
    @EntityGraph(attributePaths = {"category", "creator", "company", "company.address", "address",
            "address.city", "address.district"})
    Page<Job> searchForStaff(@Param("statuses") List<JobStatus> statuses, @Param("query") String query,
                             @Param("companyId") UUID companyId, @Param("employerId") UUID employerId,
                             Pageable pageable);

    @Query(value = "select exists(select 1 from jobs where job_category_id = :categoryId)",
            nativeQuery = true)
    boolean existsAnyByCategoryId(@Param("categoryId") UUID categoryId);
}

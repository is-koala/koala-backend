package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.Report;
import com.exskylab.koala.entities.ReportReason;
import com.exskylab.koala.entities.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportDao extends JpaRepository<Report, UUID> {

    @Query(value = """
            select r.reported_user_id as "reportedUserId", j.company_id as "jobCompanyId",
                   j.creator_id as "jobCreatorId", r.image_id as "imageId"
            from reports r
            left join jobs j on j.id = r.job_id
            where r.id = :id
            """, nativeQuery = true)
    Optional<ReportedParty> findReportedPartyById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") UUID id);

    Page<Report> findAllByReporterId(UUID reporterId, Pageable pageable);

    Optional<Report> findByReporterIdAndMessageId(UUID reporterId, UUID messageId);

    long countByReportedUserId(UUID reportedUserId);

    long countByReporterId(UUID reporterId);

    @EntityGraph(attributePaths = {"reportedUser", "reportedUser.profilePicture",
            "reviewedBy", "reviewedBy.profilePicture", "job", "message", "image"})
    Optional<Report> findDetailedById(UUID id);

    @Query(value = """
            select r from Report r
            left join r.reporter reporter
            left join r.reportedUser reported
            left join r.job job
            where (:status is null or r.status = :status)
              and (:reason is null or r.reason = :reason)
              and r.createdAt >= :from
              and r.createdAt <= :to
              and (:reportedUserId is null or r.reportedUser.id = :reportedUserId)
              and (:reporterId is null or reporter.id = :reporterId)
              and (cast(:targetType as string) is null
                   or (cast(:targetType as string) = 'USER' and r.reportedUser.id is not null)
                   or (cast(:targetType as string) = 'JOB' and r.job.id is not null)
                   or (cast(:targetType as string) = 'MESSAGE' and r.message.id is not null)
                   or (cast(:targetType as string) = 'IMAGE' and r.image.id is not null))
              and (cast(:query as string) is null
                   or unaccent(lower(reporter.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(reporter.firstName, ' ', reporter.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(reported.email)) like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(concat(reported.firstName, ' ', reported.lastName)))
                      like unaccent(lower(concat('%', cast(:query as string), '%')))
                   or unaccent(lower(job.title)) like unaccent(lower(concat('%', cast(:query as string), '%'))))
            """,
            countQuery = """
                    select count(r) from Report r
                    left join r.reporter reporter
                    left join r.reportedUser reported
                    left join r.job job
                    where (:status is null or r.status = :status)
                      and (:reason is null or r.reason = :reason)
                      and r.createdAt >= :from
                      and r.createdAt <= :to
                      and (:reportedUserId is null or r.reportedUser.id = :reportedUserId)
                      and (:reporterId is null or reporter.id = :reporterId)
                      and (cast(:targetType as string) is null
                           or (cast(:targetType as string) = 'USER' and r.reportedUser.id is not null)
                           or (cast(:targetType as string) = 'JOB' and r.job.id is not null)
                           or (cast(:targetType as string) = 'MESSAGE' and r.message.id is not null)
                           or (cast(:targetType as string) = 'IMAGE' and r.image.id is not null))
                      and (cast(:query as string) is null
                           or unaccent(lower(reporter.email))
                              like unaccent(lower(concat('%', cast(:query as string), '%')))
                           or unaccent(lower(concat(reporter.firstName, ' ', reporter.lastName)))
                              like unaccent(lower(concat('%', cast(:query as string), '%')))
                           or unaccent(lower(reported.email))
                              like unaccent(lower(concat('%', cast(:query as string), '%')))
                           or unaccent(lower(concat(reported.firstName, ' ', reported.lastName)))
                              like unaccent(lower(concat('%', cast(:query as string), '%')))
                           or unaccent(lower(job.title))
                              like unaccent(lower(concat('%', cast(:query as string), '%'))))
                    """)
    @EntityGraph(attributePaths = {"reportedUser", "reportedUser.profilePicture",
            "reviewedBy", "reviewedBy.profilePicture"})
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("reason") ReportReason reason,
                        @Param("targetType") String targetType,
                        @Param("reportedUserId") UUID reportedUserId,
                        @Param("reporterId") UUID reporterId,
                        @Param("query") String query,
                        @Param("from") Instant from,
                        @Param("to") Instant to,
                        Pageable pageable);

    @Query("""
            select r.reportedUser.id as userId, count(r) as total
            from Report r
            where r.reportedUser.id in :userIds
            group by r.reportedUser.id
            """)
    List<ReportedUserTally> tallyByReportedUser(@Param("userIds") Collection<UUID> userIds);

    @Query("""
            select r from Report r
            left join r.job job
            left join r.reporter reporter
            where job.id = :jobId
              and reporter.id in :partyIds
            order by r.createdAt desc
            """)
    List<Report> findAllBetweenAssignmentParties(@Param("jobId") UUID jobId,
                                                 @Param("partyIds") Collection<UUID> partyIds);
}

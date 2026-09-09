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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportDao extends JpaRepository<Report, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") UUID id);

    Page<Report> findAllByReporterId(UUID reporterId, Pageable pageable);

    long countByReportedUserId(UUID reportedUserId);

    @EntityGraph(attributePaths = {"reporter", "reporter.profilePicture",
            "reportedUser", "reportedUser.profilePicture",
            "reviewedBy", "reviewedBy.profilePicture", "job", "message", "image"})
    Optional<Report> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"reporter", "reporter.profilePicture",
            "reportedUser", "reportedUser.profilePicture",
            "reviewedBy", "reviewedBy.profilePicture"})
    @Query(value = """
            select r from Report r
            where (:status is null or r.status = :status)
              and (:reason is null or r.reason = :reason)
              and (:reportedUserId is null or r.reportedUser.id = :reportedUserId)
              and (cast(:targetType as string) is null
                   or (cast(:targetType as string) = 'USER' and r.reportedUser.id is not null)
                   or (cast(:targetType as string) = 'JOB' and r.job.id is not null)
                   or (cast(:targetType as string) = 'MESSAGE' and r.message.id is not null)
                   or (cast(:targetType as string) = 'IMAGE' and r.image.id is not null))
            """,
            countQuery = """
                    select count(r) from Report r
                    where (:status is null or r.status = :status)
                      and (:reason is null or r.reason = :reason)
                      and (:reportedUserId is null or r.reportedUser.id = :reportedUserId)
                      and (cast(:targetType as string) is null
                           or (cast(:targetType as string) = 'USER' and r.reportedUser.id is not null)
                           or (cast(:targetType as string) = 'JOB' and r.job.id is not null)
                           or (cast(:targetType as string) = 'MESSAGE' and r.message.id is not null)
                           or (cast(:targetType as string) = 'IMAGE' and r.image.id is not null))
                    """)
    Page<Report> search(@Param("status") ReportStatus status,
                        @Param("reason") ReportReason reason,
                        @Param("targetType") String targetType,
                        @Param("reportedUserId") UUID reportedUserId,
                        Pageable pageable);

    @Query("""
            select r.reportedUser.id as userId, count(r) as total
            from Report r
            where r.reportedUser.id in :userIds
            group by r.reportedUser.id
            """)
    List<ReportedUserTally> tallyByReportedUser(@Param("userIds") Collection<UUID> userIds);
}

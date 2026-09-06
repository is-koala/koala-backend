package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AdminAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AdminAuditEntryDao extends JpaRepository<AdminAuditEntry, UUID> {

    @Query(value = """
            select e from AdminAuditEntry e
            left join fetch e.detail
            where (:actor is null or e.actorStaffId = :actor)
              and (cast(:action as string) is null or e.action = cast(:action as string))
              and (cast(:permission as string) is null or e.permission = cast(:permission as string))
              and (cast(:resourceType as string) is null or e.resourceType = cast(:resourceType as string))
              and (cast(:resourceId as string) is null or e.resourceId = cast(:resourceId as string))
              and (:from is null or e.occurredAt >= :from)
              and (:to is null or e.occurredAt <= :to)
              and (:allowed is null or e.allowed = :allowed)
            """,
            countQuery = """
                    select count(e) from AdminAuditEntry e
                    where (:actor is null or e.actorStaffId = :actor)
                      and (cast(:action as string) is null or e.action = cast(:action as string))
                      and (cast(:permission as string) is null or e.permission = cast(:permission as string))
                      and (cast(:resourceType as string) is null or e.resourceType = cast(:resourceType as string))
                      and (cast(:resourceId as string) is null or e.resourceId = cast(:resourceId as string))
                      and (:from is null or e.occurredAt >= :from)
                      and (:to is null or e.occurredAt <= :to)
                      and (:allowed is null or e.allowed = :allowed)
                    """)
    Page<AdminAuditEntry> search(@Param("actor") UUID actor,
                                 @Param("action") String action,
                                 @Param("permission") String permission,
                                 @Param("resourceType") String resourceType,
                                 @Param("resourceId") String resourceId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("allowed") Boolean allowed,
                                 Pageable pageable);

    @Modifying
    @Query("delete from AdminAuditEntry e where e.retainUntil < :now")
    int deleteExpired(@Param("now") Instant now);
}

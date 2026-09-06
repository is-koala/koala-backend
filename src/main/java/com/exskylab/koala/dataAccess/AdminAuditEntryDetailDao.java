package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.AdminAuditEntryDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AdminAuditEntryDetailDao extends JpaRepository<AdminAuditEntryDetail, UUID> {

    @Modifying
    @Query("delete from AdminAuditEntryDetail d where d.purgeAfter < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("delete from AdminAuditEntryDetail d where d.entry.id in "
            + "(select e.id from AdminAuditEntry e where e.resourceId = :resourceId)")
    int deleteByResourceId(@Param("resourceId") String resourceId);
}

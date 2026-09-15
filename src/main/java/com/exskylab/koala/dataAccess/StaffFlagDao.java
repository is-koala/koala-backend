package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffResourceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffFlagDao extends JpaRepository<StaffFlag, UUID> {

    @EntityGraph(attributePaths = {"setBy"})
    List<StaffFlag> findAllByResourceTypeAndResourceIdInAndClearedAtIsNullOrderBySetAtDesc(
            StaffResourceType resourceType, Collection<UUID> resourceIds);

    @EntityGraph(attributePaths = {"setBy"})
    Optional<StaffFlag> findByResourceTypeAndResourceIdAndKindAndClearedAtIsNull(
            StaffResourceType resourceType, UUID resourceId, StaffFlagKind kind);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into staff_flags (id, resource_type, resource_id, kind, label, set_by_staff_id, set_at, is_deleted)
            values (:id, :resourceType, :resourceId, :kind, :label, :setByStaffId, :setAt, false)
            on conflict (resource_type, resource_id, kind) where cleared_at is null
            do update set label = excluded.label, set_by_staff_id = excluded.set_by_staff_id, set_at = excluded.set_at
            """, nativeQuery = true)
    int upsertActive(@Param("id") UUID id,
                     @Param("resourceType") String resourceType,
                     @Param("resourceId") UUID resourceId,
                     @Param("kind") String kind,
                     @Param("label") String label,
                     @Param("setByStaffId") UUID setByStaffId,
                     @Param("setAt") Instant setAt);
}

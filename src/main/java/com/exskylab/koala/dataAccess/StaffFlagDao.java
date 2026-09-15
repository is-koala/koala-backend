package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffResourceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}

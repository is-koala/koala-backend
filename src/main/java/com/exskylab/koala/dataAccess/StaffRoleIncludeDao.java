package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffRoleInclude;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffRoleIncludeDao extends JpaRepository<StaffRoleInclude, UUID> {

    List<StaffRoleInclude> findAllByParentRoleIdIn(Collection<UUID> parentRoleIds);

    @EntityGraph(attributePaths = {"parentRole", "childRole"})
    List<StaffRoleInclude> findAllWithRolesByParentRoleIdIn(Collection<UUID> parentRoleIds);

    @EntityGraph(attributePaths = {"parentRole", "childRole"})
    List<StaffRoleInclude> findAllWithRolesByChildRoleIdIn(Collection<UUID> childRoleIds);

    boolean existsByParentRoleIdAndChildRoleId(UUID parentRoleId, UUID childRoleId);

    void deleteByParentRoleIdAndChildRoleId(UUID parentRoleId, UUID childRoleId);
}

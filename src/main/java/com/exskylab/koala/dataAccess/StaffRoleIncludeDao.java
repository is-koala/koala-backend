package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffRoleInclude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffRoleIncludeDao extends JpaRepository<StaffRoleInclude, UUID> {

    List<StaffRoleInclude> findAllByParentRoleIdIn(Collection<UUID> parentRoleIds);

    boolean existsByParentRoleIdAndChildRoleId(UUID parentRoleId, UUID childRoleId);
}

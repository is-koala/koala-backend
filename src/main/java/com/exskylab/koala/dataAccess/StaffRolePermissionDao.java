package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffRolePermission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffRolePermissionDao extends JpaRepository<StaffRolePermission, UUID> {

    List<StaffRolePermission> findAllByRoleIdIn(Collection<UUID> roleIds);

    @EntityGraph(attributePaths = "role")
    List<StaffRolePermission> findAllWithRoleByRoleIdIn(Collection<UUID> roleIds);

    boolean existsByRoleIdAndPermissionCode(UUID roleId, String permissionCode);

    void deleteByRoleIdAndPermissionCode(UUID roleId, String permissionCode);
}

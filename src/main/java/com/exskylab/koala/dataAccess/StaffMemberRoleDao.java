package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.StaffMemberRole;
import com.exskylab.koala.entities.StaffMemberRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StaffMemberRoleDao extends JpaRepository<StaffMemberRole, StaffMemberRoleId> {

    @EntityGraph(attributePaths = "role")
    List<StaffMemberRole> findAllByMemberId(UUID memberId);

    @EntityGraph(attributePaths = {"member", "role"})
    List<StaffMemberRole> findAllByMemberIdIn(Collection<UUID> memberIds);

    @EntityGraph(attributePaths = {"member", "role"})
    List<StaffMemberRole> findAllByRoleIdIn(Collection<UUID> roleIds);

    boolean existsByMemberIdAndRoleId(UUID memberId, UUID roleId);

    @Modifying
    @Query("delete from StaffMemberRole r where r.member.id = :memberId and r.role.id = :roleId")
    int deleteMembership(@Param("memberId") UUID memberId, @Param("roleId") UUID roleId);

    @Query("select count(r) from StaffMemberRole r where r.role.id in :roleIds and r.member.active = true")
    long countActiveMembersInRoles(@Param("roleIds") Collection<UUID> roleIds);
}

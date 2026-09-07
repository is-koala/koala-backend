package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.StaffRoleView;
import com.exskylab.koala.entities.StaffRole;
import com.exskylab.koala.entities.StaffPermission;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StaffAuthorizationService {

    StaffAuthorization resolve(Collection<String> roleCodes);

    List<StaffPermission> getPermissions();

    List<StaffRoleView> getRoles();

    StaffRoleView getRole(UUID roleId);

    StaffRole getRoleByCode(String roleCode);

    void grantPermission(StaffPrincipal actor, UUID roleId, StaffPermission permission);

    void revokePermission(StaffPrincipal actor, UUID roleId, StaffPermission permission);

    void includeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId);

    void excludeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId);
}

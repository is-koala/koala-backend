package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.StaffRoleView;
import com.exskylab.koala.entities.StaffMember;
import com.exskylab.koala.entities.StaffPermission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAuthorizationService {

    Optional<StaffMember> findActiveMember(String keycloakSubject);

    StaffAuthorization resolve(Collection<String> roleCodes);

    List<StaffRoleView> getRoles();

    StaffRoleView getRole(UUID roleId);

    void grantPermission(StaffPrincipal actor, UUID roleId, StaffPermission permission);

    void revokePermission(StaffPrincipal actor, UUID roleId, StaffPermission permission);

    void includeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId);

    void excludeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId);
}

package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffMember;
import com.exskylab.koala.entities.StaffPermission;

import java.util.Collection;
import java.util.Optional;

public interface StaffAuthorizationService {

    Optional<StaffMember> findActiveMember(String keycloakSubject);

    StaffAuthorization resolve(Collection<String> roleCodes);

    void grantPermission(StaffPrincipal actor, String roleCode, StaffPermission permission);

    void revokePermission(StaffPrincipal actor, String roleCode, StaffPermission permission);

    void includeRole(StaffPrincipal actor, String parentRoleCode, String childRoleCode);
}

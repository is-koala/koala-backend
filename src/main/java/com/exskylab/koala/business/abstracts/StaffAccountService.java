package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.staff.response.StaffTokenDto;
import com.exskylab.koala.core.security.StaffBootstrapResult;
import com.exskylab.koala.core.security.StaffMemberView;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.StaffMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface StaffAccountService {

    StaffTokenDto login(String email, String rawPassword);

    StaffTokenDto refresh(String refreshToken);

    void logout(StaffPrincipal actor);

    Optional<StaffPrincipal> principalOf(UUID staffId, int tokenVersion);

    StaffMember getReference(UUID staffId);

    void changeOwnPassword(StaffPrincipal actor, String currentPassword, String newPassword);

    Page<StaffMemberView> search(String query, Pageable pageable);

    StaffMemberView get(UUID staffId);

    StaffMemberView create(String email, String displayName, String rawPassword);

    StaffMemberView setActive(StaffPrincipal actor, UUID staffId, boolean active);

    void grantRole(StaffPrincipal actor, UUID staffId, String roleCode);

    void revokeRole(StaffPrincipal actor, UUID staffId, String roleCode);

    void resetPassword(StaffPrincipal actor, UUID staffId, String rawPassword);

    StaffBootstrapResult bootstrapOwner(String email, String rawPassword);

    Map<UUID, String> displayNamesOf(Collection<UUID> staffIds);
}

package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffRole;

import java.util.List;
import java.util.Set;

public record StaffRoleView(StaffRole role, Set<StaffPermission> permissions, List<StaffRole> includes,
                            Set<StaffPermission> effectivePermissions, List<StaffRole> effectiveRoles) {
}

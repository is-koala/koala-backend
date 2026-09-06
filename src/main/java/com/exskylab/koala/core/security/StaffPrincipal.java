package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.StaffPermission;

import java.util.Set;
import java.util.UUID;

public record StaffPrincipal(UUID staffId, String subject, String email, String displayName,
                             Set<String> roles, Set<StaffPermission> permissions) {

    public static StaffPrincipal unregistered(String subject, Set<String> roles) {
        return new StaffPrincipal(null, subject, null, null, Set.copyOf(roles), Set.of());
    }

    public boolean isRegistered() {
        return staffId != null;
    }

    public boolean holds(StaffPermission permission) {
        return permissions.contains(permission);
    }
}

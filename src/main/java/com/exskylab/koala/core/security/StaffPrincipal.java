package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.StaffPermission;

import java.util.Set;
import java.util.UUID;

public record StaffPrincipal(UUID staffId, String email, String displayName, Set<String> roles,
                             Set<StaffPermission> permissions, boolean mustChangePassword) {

    public static StaffPrincipal unregistered(String email, Set<String> roles) {
        return new StaffPrincipal(null, email, null, Set.copyOf(roles), Set.of(), false);
    }

    public boolean isRegistered() {
        return staffId != null;
    }

    public boolean holds(StaffPermission permission) {
        return permissions.contains(permission);
    }
}

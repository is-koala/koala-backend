package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.StaffPermission;

import java.util.Set;

public record StaffAuthorization(Set<String> roles, Set<StaffPermission> permissions) {

    public static StaffAuthorization empty() {
        return new StaffAuthorization(Set.of(), Set.of());
    }
}

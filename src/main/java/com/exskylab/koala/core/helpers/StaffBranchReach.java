package com.exskylab.koala.core.helpers;

import java.util.Collection;
import java.util.Set;

public final class StaffBranchReach {

    private final Set<String> assignedRoleCodes;
    private final Set<String> reachedRoleCodes;

    private StaffBranchReach(Set<String> assignedRoleCodes, Set<String> reachedRoleCodes) {
        this.assignedRoleCodes = assignedRoleCodes;
        this.reachedRoleCodes = reachedRoleCodes;
    }

    public static Assigned assignedRole(String assignedRoleCode) {
        return new Assigned(Set.of(assignedRoleCode));
    }

    public static Assigned assignedRoles(Collection<String> assignedRoleCodes) {
        return new Assigned(Set.copyOf(assignedRoleCodes));
    }

    public Set<String> assignedRoleCodes() {
        return assignedRoleCodes;
    }

    public Set<String> reachedRoleCodes() {
        return reachedRoleCodes;
    }

    public static final class Assigned {

        private final Set<String> assignedRoleCodes;

        private Assigned(Set<String> assignedRoleCodes) {
            this.assignedRoleCodes = assignedRoleCodes;
        }

        public StaffBranchReach reaching(Collection<String> reachedRoleCodes) {
            return new StaffBranchReach(assignedRoleCodes, Set.copyOf(reachedRoleCodes));
        }
    }
}

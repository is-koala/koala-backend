package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ConflictException;

import java.util.Collection;
import java.util.Set;

public final class StaffSeparationOfDuties {

    public static final String OWNER_ROLE_CODE = "OWNER";

    private static final Set<String> TRUST_BRANCH_ROLE_CODES =
            Set.of("MODERATOR", "DISPUTE_HANDLER", "TRUST_LEAD");

    private static final String FINANCE_BRANCH_ROLE_CODE_PREFIX = "FINANCE_";

    private StaffSeparationOfDuties() {
    }

    public static boolean isOwnerExemption(String roleCode) {
        return OWNER_ROLE_CODE.equals(roleCode);
    }

    public static boolean isTrustBranch(String roleCode) {
        return TRUST_BRANCH_ROLE_CODES.contains(roleCode);
    }

    public static boolean isFinanceBranch(String roleCode) {
        return roleCode != null && roleCode.startsWith(FINANCE_BRANCH_ROLE_CODE_PREFIX);
    }

    public static boolean spansBothBranches(Collection<String> roleCodes) {
        return roleCodes.stream().anyMatch(StaffSeparationOfDuties::isTrustBranch)
                && roleCodes.stream().anyMatch(StaffSeparationOfDuties::isFinanceBranch);
    }

    public static void requireBranchesStaySeparate(Collection<String> assignedRoleCodes,
                                                   Collection<String> reachedRoleCodes) {
        boolean exemptBecauseOwnerIsAssigned =
                assignedRoleCodes.stream().anyMatch(StaffSeparationOfDuties::isOwnerExemption);

        if (exemptBecauseOwnerIsAssigned) {
            return;
        }

        if (spansBothBranches(reachedRoleCodes)) {
            throw new ConflictException(StaffMessages.SEPARATION_OF_DUTIES_VIOLATED);
        }
    }
}

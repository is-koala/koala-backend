package com.exskylab.koala.entities;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum StaffPermission {

    CATEGORY_MANAGE("category.manage", true, PermissionSensitivity.NORMAL),
    BADGE_MANAGE("badge.manage", true, PermissionSensitivity.NORMAL),
    SETTINGS_MANAGE("settings.manage", true, PermissionSensitivity.NORMAL),
    CONTENT_MODERATE("content.moderate", true, PermissionSensitivity.NORMAL),
    USER_READ("user.read", false, PermissionSensitivity.NORMAL),
    USER_READ_PII("user.read_pii", false, PermissionSensitivity.PERSONAL_DATA),
    USER_DELETION_MANAGE("user.deletion.manage", true, PermissionSensitivity.PERSONAL_DATA),
    USER_ROLE_MANAGE("user.role.manage", true, PermissionSensitivity.NORMAL),
    COMPANY_APPROVE("company.approve", true, PermissionSensitivity.NORMAL),
    IDENTITY_REVIEW("identity.review", true, PermissionSensitivity.PERSONAL_DATA),
    DISPUTE_RESOLVE("dispute.resolve", true, PermissionSensitivity.NORMAL),
    JOB_READ("job.read", false, PermissionSensitivity.PERSONAL_DATA),
    PAYMENT_READ("payment.read", false, PermissionSensitivity.FINANCIAL),
    FINANCE_READ("finance.read", false, PermissionSensitivity.FINANCIAL),
    PAYOUT_RELEASE("payout.release", true, PermissionSensitivity.FINANCIAL),
    PAYMENT_REFUND("payment.refund", true, PermissionSensitivity.FINANCIAL),
    PAYOUT_RECONCILE("payout.reconcile", true, PermissionSensitivity.FINANCIAL),
    BANK_ACCOUNT_VERIFY("bank.account.verify", true, PermissionSensitivity.FINANCIAL),
    STAFF_READ("staff.read", false, PermissionSensitivity.PERSONAL_DATA),
    STAFF_MANAGE("staff.manage", true, PermissionSensitivity.PERSONAL_DATA),
    ROLE_MANAGE("role.manage", true, PermissionSensitivity.NORMAL),
    AUDIT_READ("audit.read", false, PermissionSensitivity.PERSONAL_DATA),
    NOTE_READ("note.read", false, PermissionSensitivity.PERSONAL_DATA),
    NOTE_WRITE("note.write", true, PermissionSensitivity.PERSONAL_DATA),
    FLAG_MANAGE("flag.manage", true, PermissionSensitivity.NORMAL);

    private static final Map<String, StaffPermission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(StaffPermission::getCode, Function.identity()));

    private final String code;
    private final boolean mutating;
    private final PermissionSensitivity sensitivity;

    StaffPermission(String code, boolean mutating, PermissionSensitivity sensitivity) {
        this.code = code;
        this.mutating = mutating;
        this.sensitivity = sensitivity;
    }

    public String getCode() {
        return code;
    }

    public boolean isMutating() {
        return mutating;
    }

    public PermissionSensitivity getSensitivity() {
        return sensitivity;
    }

    public static Optional<StaffPermission> fromCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}

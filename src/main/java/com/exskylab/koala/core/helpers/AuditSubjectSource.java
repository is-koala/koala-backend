package com.exskylab.koala.core.helpers;

import java.util.Arrays;
import java.util.Optional;

public enum AuditSubjectSource {

    USER("users", Resolution.PATH, "userId", null),
    COMPANY("companies", Resolution.PATH, "companyId", null),
    IDENTITY_REVIEW("identity-verifications", Resolution.PATH, "reviewId", null),
    DELETION_REQUEST("account-deletions", Resolution.PATH, "requestId", null),
    IMAGE("images", Resolution.PATH, "imageId", null),
    BANK_ACCOUNT_VERIFICATION("bank-account-verifications", Resolution.PATH, "reference", null),
    JOB("jobs", Resolution.PATH, "jobId", null),
    ASSIGNMENT("job-assignments", Resolution.PATH, "assignmentId", null),
    PAYOUT("payouts", Resolution.PATH, "payoutId", null),
    PAYMENT("payments", Resolution.PATH, "paymentId", null),
    DISPUTE("disputes", Resolution.PATH, "disputeId", null),
    REPORT("reports", Resolution.PATH, "reportId", null),
    STAFF_NOTES("notes", Resolution.SERVICE, null,
            "hedef istek gövdesinde/kayıtta; StaffNoteManager konuyu notun hedefinden çözüp "
                    + "StaffAuditContext.about ile damgalar"),
    STAFF_FLAGS("flags", Resolution.SERVICE, null,
            "hedef istek parametresinde; StaffNoteManager konuyu işaretin hedefinden çözüp "
                    + "StaffAuditContext.about ile damgalar"),
    BADGE_CATALOGUE("badges", Resolution.NONE, null,
            "rozet kataloğu; karar bir kullanıcı ya da şirket hakkında değil"),
    CATEGORY_CATALOGUE("job-categories", Resolution.NONE, null,
            "iş kategorisi kataloğu; karar bir kullanıcı ya da şirket hakkında değil"),
    RUNTIME_SETTINGS("settings", Resolution.NONE, null,
            "çalışma zamanı ayarı; tüm pazaryerini etkiler, tek bir konusu yok"),
    STAFF_ACCOUNTS("staff", Resolution.NONE, null,
            "personel hesabı; konu türleri pazaryeri tarafıdır (USER|COMPANY), personel değil"),
    STAFF_ROLES("roles", Resolution.NONE, null,
            "personel rol grafiği; konu türleri pazaryeri tarafıdır, personel değil"),
    CLIENT_EVENTS("audit-log", Resolution.SERVICE, null,
            "istemci olayının satırını yetki kararı değil StaffAuditManager.recordClientEvent yazar; konuyu "
                    + "gövdedeki subjectType/subjectId'den, varlığını doğrulayarak o verir");

    public enum Resolution {
        PATH,
        SERVICE,
        NONE
    }

    private final String resourceType;
    private final Resolution resolution;
    private final String variable;
    private final String reason;

    AuditSubjectSource(String resourceType, Resolution resolution, String variable, String reason) {
        this.resourceType = resourceType;
        this.resolution = resolution;
        this.variable = variable;
        this.reason = reason;
    }

    public static Optional<AuditSubjectSource> of(String resourceType) {
        return Arrays.stream(values())
                .filter(source -> source.resourceType.equals(resourceType))
                .findFirst();
    }

    public String getResourceType() {
        return resourceType;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public String getVariable() {
        return variable;
    }

    public String getReason() {
        return reason;
    }

    public boolean resolvesFromThePath() {
        return resolution == Resolution.PATH;
    }
}

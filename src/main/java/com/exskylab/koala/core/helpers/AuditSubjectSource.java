package com.exskylab.koala.core.helpers;

import java.util.Arrays;
import java.util.Optional;

public enum AuditSubjectSource {

    USER("users", "userId", null),
    COMPANY("companies", "companyId", null),
    IDENTITY_REVIEW("identity-verifications", "reviewId", null),
    DELETION_REQUEST("account-deletions", "requestId", null),
    IMAGE("images", "imageId", null),
    BANK_ACCOUNT_VERIFICATION("bank-account-verifications", "reference", null),
    ASSIGNMENT("job-assignments", "assignmentId", null),
    PAYOUT("payouts", "payoutId", null),
    DISPUTE("disputes", "disputeId", null),
    REPORT("reports", "reportId", null),
    BADGE_CATALOGUE("badges", null,
            "rozet kataloğu; karar bir kullanıcı ya da şirket hakkında değil"),
    CATEGORY_CATALOGUE("job-categories", null,
            "iş kategorisi kataloğu; karar bir kullanıcı ya da şirket hakkında değil"),
    RUNTIME_SETTINGS("settings", null,
            "çalışma zamanı ayarı; tüm pazaryerini etkiler, tek bir konusu yok"),
    STAFF_ACCOUNTS("staff", null,
            "personel hesabı; konu türleri pazaryeri tarafıdır (USER|COMPANY), personel değil"),
    STAFF_ROLES("roles", null,
            "personel rol grafiği; konu türleri pazaryeri tarafıdır, personel değil"),
    STAFF_NOTES("notes", null,
            "hedef istek gövdesinde ve not kendi tablosunda resourceType/resourceId ile tutulur; "
                    + "GET /admin/notes hedefe göre listeler"),
    STAFF_FLAGS("flags", null,
            "hedef istek gövdesinde/parametresinde ve işaret kendi tablosunda resourceType/resourceId ile "
                    + "tutulur; GET /admin/flags hedefe göre listeler");

    private final String resourceType;
    private final String variable;
    private final String reasonWithoutSubject;

    AuditSubjectSource(String resourceType, String variable, String reasonWithoutSubject) {
        this.resourceType = resourceType;
        this.variable = variable;
        this.reasonWithoutSubject = reasonWithoutSubject;
    }

    public static Optional<AuditSubjectSource> of(String resourceType) {
        return Arrays.stream(values())
                .filter(source -> source.resourceType.equals(resourceType))
                .findFirst();
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getVariable() {
        return variable;
    }

    public String getReasonWithoutSubject() {
        return reasonWithoutSubject;
    }

    public boolean resolvesASubject() {
        return variable != null;
    }
}

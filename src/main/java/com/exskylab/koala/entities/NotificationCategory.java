package com.exskylab.koala.entities;

public enum NotificationCategory {

    ACCOUNT_SECURITY(NotificationCategoryKind.TRANSACTIONAL),
    APPLICATION_UPDATES(NotificationCategoryKind.TRANSACTIONAL),
    IDENTITY_VERIFICATION(NotificationCategoryKind.TRANSACTIONAL),
    MARKETINGS(NotificationCategoryKind.COMMERCIAL),
    COMPANY_UPDATES(NotificationCategoryKind.TRANSACTIONAL),
    JOB_ALERTS(NotificationCategoryKind.ALERT),
    MESSAGES(NotificationCategoryKind.ALERT),
    NEWSLETTER(NotificationCategoryKind.COMMERCIAL);

    private final NotificationCategoryKind kind;

    NotificationCategory(NotificationCategoryKind kind) {
        this.kind = kind;
    }

    public NotificationCategoryKind getKind() {
        return kind;
    }

    public boolean isOptional() {
        return kind.isOptional();
    }
}

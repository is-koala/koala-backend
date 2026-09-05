package com.exskylab.koala.entities;

public enum NotificationCategoryKind {

    TRANSACTIONAL(false, true, false),
    ALERT(true, true, false),
    COMMERCIAL(true, false, true);

    private final boolean optional;
    private final boolean enabledByDefault;
    private final boolean commercial;

    NotificationCategoryKind(boolean optional, boolean enabledByDefault, boolean commercial) {
        this.optional = optional;
        this.enabledByDefault = enabledByDefault;
        this.commercial = commercial;
    }

    public boolean isOptional() {
        return optional;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public boolean isCommercial() {
        return commercial;
    }
}

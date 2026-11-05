package com.exskylab.koala.core.security;

public record AuditSubjects(AuditSubject subject, AuditSubject related) {

    public static final AuditSubjects NONE = new AuditSubjects(null, null);

    public static AuditSubjects of(AuditSubject subject) {
        return subject == null ? NONE : new AuditSubjects(subject, null);
    }

    public static AuditSubjects of(AuditSubject subject, AuditSubject related) {
        return subject == null ? of(related) : new AuditSubjects(subject, related);
    }
}

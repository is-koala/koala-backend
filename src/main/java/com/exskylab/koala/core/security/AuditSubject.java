package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.AuditSubjectType;

import java.util.Objects;
import java.util.UUID;

public record AuditSubject(AuditSubjectType type, UUID id) {

    public AuditSubject {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static AuditSubject user(UUID id) {
        return new AuditSubject(AuditSubjectType.USER, id);
    }

    public static AuditSubject company(UUID id) {
        return new AuditSubject(AuditSubjectType.COMPANY, id);
    }

    public static AuditSubject employerOf(UUID companyId, UUID creatorId) {
        if (companyId != null) {
            return company(companyId);
        }
        return creatorId == null ? null : user(creatorId);
    }
}

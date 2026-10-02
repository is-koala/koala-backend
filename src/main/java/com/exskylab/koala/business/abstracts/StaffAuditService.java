package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.entities.AdminAuditEntry;
import com.exskylab.koala.entities.AuditSubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface StaffAuditService {

    AdminAuditEntry record(StaffAuditRecord source, String requestBody, String before, String after);

    Page<AdminAuditEntry> search(UUID actor, String action, String permission, String resourceType,
                                 String resourceId, AuditSubjectType subjectType, UUID subjectId,
                                 Instant from, Instant to, Boolean allowed, Pageable pageable);

    int purgeExpiredDetails();

    int purgeExpiredEntries();

    int eraseDetailsForResource(String resourceId);
}

package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.StaffAuditRecord;
import com.exskylab.koala.entities.AdminAuditEntry;

public interface StaffAuditService {

    AdminAuditEntry record(StaffAuditRecord source, String requestBody, String before, String after);

    int purgeExpiredDetails();

    int purgeExpiredEntries();

    int eraseDetailsForResource(String resourceId);
}

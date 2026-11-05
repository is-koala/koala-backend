package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.audit.request.ClientAuditEventDto;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.entities.AdminAuditEntry;

public interface ClientAuditEventService {

    AdminAuditEntry record(StaffPrincipal actor, ClientAuditEventDto event, String ip, String userAgent);
}

package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.AuditSubject;
import com.exskylab.koala.core.security.AuditSubjects;
import com.exskylab.koala.entities.StaffResourceType;

import java.util.UUID;

public interface AuditSubjectService {

    AuditSubjects resolve(String resourceType, String resourceId);

    AuditSubjects forStaffTarget(StaffResourceType resourceType, UUID resourceId);

    boolean exists(AuditSubject subject);
}

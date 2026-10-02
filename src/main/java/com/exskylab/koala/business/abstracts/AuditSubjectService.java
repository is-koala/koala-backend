package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.security.AuditSubjects;

public interface AuditSubjectService {

    AuditSubjects resolve(String resourceType, String resourceId);
}

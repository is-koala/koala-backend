package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.dispute.response.EvidenceScrubReportDto;
import com.exskylab.koala.core.security.StaffPrincipal;

public interface DisputeEvidenceScrubService {

    EvidenceScrubReportDto scrubStoredEvidence(StaffPrincipal actor, int limit, boolean retryFailed);
}

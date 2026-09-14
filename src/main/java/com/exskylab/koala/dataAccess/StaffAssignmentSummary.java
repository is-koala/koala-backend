package com.exskylab.koala.dataAccess;

import java.util.UUID;

public interface StaffAssignmentSummary {

    UUID getId();

    UUID getWorkerId();

    UUID getJobId();

    String getJobTitle();

    UUID getEmployerId();

    UUID getCompanyId();

    String getCompanyName();
}

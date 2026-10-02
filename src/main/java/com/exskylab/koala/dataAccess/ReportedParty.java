package com.exskylab.koala.dataAccess;

import java.util.UUID;

public interface ReportedParty {

    UUID getReportedUserId();

    UUID getJobCompanyId();

    UUID getJobCreatorId();
}

package com.exskylab.koala.dataAccess;

import java.util.UUID;

public interface ReportedUserTally {

    UUID getUserId();

    long getTotal();
}

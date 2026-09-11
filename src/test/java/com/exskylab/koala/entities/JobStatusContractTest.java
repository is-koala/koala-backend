package com.exskylab.koala.entities;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobStatusContractTest {

    @Test
    void theMarketplaceJobHasThreeStatuses() {
        assertEquals(EnumSet.of(JobStatus.OPEN, JobStatus.CLOSED, JobStatus.CANCELLED),
                EnumSet.allOf(JobStatus.class));
    }
}

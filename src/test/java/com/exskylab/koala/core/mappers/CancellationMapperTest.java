package com.exskylab.koala.core.mappers;

import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellationMapperTest {

    private final CancellationMapper mapper = new CancellationMapperImpl();

    @Test
    void cancellationCarriesTheLatestRefundLifecycle() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment);
        Refund refund = new Refund();
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(Instant.parse("2026-09-06T12:30:00Z"));

        var result = mapper.toCancellationDto(cancellation, refund);

        assertEquals(RefundStatus.COMPLETED, result.refundStatus());
        assertEquals(refund.getCompletedAt(), result.refundCompletedAt());
    }
}

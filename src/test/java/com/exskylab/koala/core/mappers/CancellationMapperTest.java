package com.exskylab.koala.core.mappers;

import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Refund;
import com.exskylab.koala.entities.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CancellationMapperTest {

    private final CancellationMapper mapper = new CancellationMapperImpl();

    @Test
    void cancellationCarriesOnlyItsProducedRefundLifecycle() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment);
        cancellation.setFeeRate(new BigDecimal("0.0750"));
        cancellation.setFeeAmount(new BigDecimal("1E+3"));
        cancellation.setRefundAmount(new BigDecimal("1050.11"));
        Refund refund = new Refund();
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(Instant.parse("2026-09-06T12:30:00Z"));
        cancellation.setRefund(refund);

        var result = mapper.toCancellationDto(cancellation);

        assertEquals(RefundStatus.COMPLETED, result.refundStatus());
        assertEquals(refund.getCompletedAt(), result.refundCompletedAt());
        assertEquals("0.0750", result.feeRate());
        assertEquals("1000", result.feeAmount());
        assertEquals("1050.11", result.refundAmount());
    }

    @Test
    void legacyCancellationWithoutACausalRefundHasNoRefundLifecycle() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        Cancellation cancellation = new Cancellation();
        cancellation.setJobAssignment(assignment);
        cancellation.setFeeRate(BigDecimal.ZERO);
        cancellation.setFeeAmount(BigDecimal.ZERO);
        cancellation.setRefundAmount(BigDecimal.ZERO);

        var result = mapper.toCancellationDto(cancellation);

        assertNull(result.refundStatus());
        assertNull(result.refundCompletedAt());
    }
}

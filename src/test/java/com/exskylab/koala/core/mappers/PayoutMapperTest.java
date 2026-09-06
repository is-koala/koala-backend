package com.exskylab.koala.core.mappers;

import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutMapperTest {

    private final PayoutMapper mapper = new PayoutMapperImpl();

    @Test
    void amountRemainsAnExactPlainDecimalStringAtTheApiBoundary() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        Payout payout = new Payout();
        payout.setJobAssignment(assignment);
        payout.setAmount(new BigDecimal("9007199254740993.99"));

        var result = mapper.toPayoutDto(payout);

        assertEquals("9007199254740993.99", result.getAmount());
    }

    @Test
    void exponentNotationIsExpandedBeforeItLeavesTheBackend() {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        Payout payout = new Payout();
        payout.setJobAssignment(assignment);
        payout.setAmount(new BigDecimal("1E+10"));

        var result = mapper.toPayoutDto(payout);

        assertEquals("10000000000", result.getAmount());
    }
}

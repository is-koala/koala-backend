package com.exskylab.koala.core.mappers;

import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutMapperTest {

    private final PayoutMapper mapper = new PayoutMapperImpl(new UserMapperImpl(
            new MediaUrlResolver(new StorageUrlResolver(new StorageProperties(),
                    new AppProperties("http://localhost:8080", "http://localhost:3000"))),
            new AddressMapperImpl(), new EducationMapperImpl(), new ExperienceMapperImpl()));

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

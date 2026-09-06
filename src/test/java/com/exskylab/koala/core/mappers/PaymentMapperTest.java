package com.exskylab.koala.core.mappers;

import com.exskylab.koala.entities.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapperImpl(new PricingDtoMapper());

    @Test
    void attemptMoneyRemainsExactPlainDecimalStringsAtTheApiBoundary() {
        Payment payment = new Payment();
        payment.setWorkerPayout(new BigDecimal("9007199254740993.99"));
        payment.setCommissionAmount(new BigDecimal("1E+2"));
        payment.setCommissionRate(new BigDecimal("0.1000"));
        payment.setTotalCharged(new BigDecimal("9007199254741093.99"));

        var result = mapper.toPaymentAttemptDto(payment);

        assertEquals("9007199254740993.99", result.getWorkerPayout());
        assertEquals("100", result.getCommissionAmount());
        assertEquals("0.1000", result.getCommissionRate());
        assertEquals("9007199254741093.99", result.getTotalCharged());
    }
}

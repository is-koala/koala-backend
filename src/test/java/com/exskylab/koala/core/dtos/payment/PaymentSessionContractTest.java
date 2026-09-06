package com.exskylab.koala.core.dtos.payment;

import com.exskylab.koala.core.dtos.jobAssignment.response.PaymentSessionDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentSessionContractTest {

    @Test
    void publicPaymentSessionNeverContainsTheProviderToken() throws Exception {
        PaymentSessionDto session = new PaymentSessionDto();
        session.setPaymentPageUrl("https://sandbox-api.iyzipay.com/checkoutform/example");

        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(session));

        assertFalse(json.has("token"));
    }
}

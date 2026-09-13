package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.PaymentReturnProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentReturnUrlBuilderTest {

    private static final UUID ASSIGNMENT_ID = UUID.fromString("352e3e2f-68df-47b8-baf2-31d03b8200a7");

    @Test
    void customSchemeUsesPaymentResultAsTheHostWithAnEmptyPath() {
        PaymentReturnUrlBuilder builder = new PaymentReturnUrlBuilder(properties("koala://"));

        assertEquals(
                "koala://payment-result?status=success&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                builder.resultUrl(true, ASSIGNMENT_ID));
    }

    @Test
    void httpsOriginUsesTheWebPaymentResultPath() {
        PaymentReturnUrlBuilder builder = new PaymentReturnUrlBuilder(properties("https://iskoala.com"));

        assertEquals(
                "https://iskoala.com/payment/result?status=failed&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                builder.resultUrl(false, ASSIGNMENT_ID));
    }

    @Test
    void anUnknownAttemptLandsOnTheSameResultPageAsFailedWithoutAnAssignment() {
        assertEquals("koala://payment-result?status=failed",
                new PaymentReturnUrlBuilder(properties("koala://")).unknownAttemptResultUrl());
        assertEquals("https://iskoala.com/payment/result?status=failed",
                new PaymentReturnUrlBuilder(properties("https://iskoala.com")).unknownAttemptResultUrl());
    }

    @Test
    void returnBaseUrlAcceptsHttpsOriginsAndKnownAppSchemesOnly() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertTrue(validator.validate(properties("https://iskoala.com")).isEmpty());
            assertTrue(validator.validate(properties("koala-dev://")).isEmpty());
            assertTrue(validator.validate(properties("koala-staging://")).isEmpty());
            assertTrue(validator.validate(properties("koala://")).isEmpty());
            assertFalse(validator.validate(properties("http://iskoala.com")).isEmpty());
            assertFalse(validator.validate(properties("javascript://payment-result")).isEmpty());
            assertFalse(validator.validate(properties("https://iskoala.com/redirect?target=evil")).isEmpty());
        }
    }

    @Test
    void returnTargetIsNormalizedBeforeUrlConstruction() {
        assertEquals("koala-staging",
                ((PaymentReturnProperties.AppScheme) properties("koala-staging://").returnTarget()).value());
        assertEquals("https://iskoala.com",
                ((PaymentReturnProperties.WebOrigin) properties("https://iskoala.com").returnTarget())
                        .value().toString());
    }

    private PaymentReturnProperties properties(String returnBaseUrl) {
        PaymentReturnProperties properties = new PaymentReturnProperties();
        properties.setReturnBaseUrl(returnBaseUrl);
        return properties;
    }
}

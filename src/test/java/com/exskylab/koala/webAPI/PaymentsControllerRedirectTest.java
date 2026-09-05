package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.helpers.PaymentReturnUrlBuilder;
import com.exskylab.koala.core.properties.PaymentReturnProperties;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentsControllerRedirectTest {

    @Test
    void callbackRedirectUsesThePaymentReturnBaseInsteadOfTheWebBase() {
        UUID assignmentId = UUID.fromString("352e3e2f-68df-47b8-baf2-31d03b8200a7");
        Payment payment = new Payment();
        payment.setStatus(PaymentAttemptStatus.SUCCEEDED);
        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);
        payment.setJobAssignment(assignment);

        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.completeFromCallback("provider-token")).thenReturn(payment);

        PaymentReturnProperties properties = new PaymentReturnProperties();
        properties.setReturnBaseUrl("koala://");
        PaymentsController controller = new PaymentsController(
                paymentService, null, new PaymentReturnUrlBuilder(properties), null);

        assertEquals(
                "koala://payment-result?status=success&assignmentId=352e3e2f-68df-47b8-baf2-31d03b8200a7",
                controller.handleProviderCallback("provider-token").getUrl());
    }
}

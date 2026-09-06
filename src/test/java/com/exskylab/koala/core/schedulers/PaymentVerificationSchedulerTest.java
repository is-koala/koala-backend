package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PaymentVerificationSchedulerTest {

    @Test
    void everyDueAttemptIsReconciledOnce() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentVerificationProperties properties = new PaymentVerificationProperties();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(paymentService.findDueVerificationAttemptIds()).thenReturn(List.of(first, second));

        new PaymentVerificationScheduler(paymentService, properties).runDueVerifications();

        verify(paymentService).reconcileAttempt(first);
        verify(paymentService).reconcileAttempt(second);
    }

    @Test
    void theKillSwitchPreventsAnyVerificationCall() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentVerificationProperties properties = new PaymentVerificationProperties();
        properties.setEnabled(false);

        new PaymentVerificationScheduler(paymentService, properties).runDueVerifications();

        verifyNoInteractions(paymentService);
    }
}

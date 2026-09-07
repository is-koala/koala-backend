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
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(paymentService.findDueVerificationAttemptIds()).thenReturn(List.of(first, second));

        scheduler(paymentService, new PaymentVerificationProperties()).reconcileDueAttempts();

        verify(paymentService).reconcileAttempt(first);
        verify(paymentService).reconcileAttempt(second);
    }

    @Test
    void everyStuckInitiationIsSweptOnce() {
        PaymentService paymentService = mock(PaymentService.class);
        UUID stuck = UUID.randomUUID();
        when(paymentService.findStuckInitiationIds()).thenReturn(List.of(stuck));

        scheduler(paymentService, new PaymentVerificationProperties()).sweepAbandonedInitiations();

        verify(paymentService).abandonStuckInitiation(stuck);
    }

    @Test
    void theSweepNeverReachesIntoTheReconciliationScan() {
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.findStuckInitiationIds()).thenReturn(List.of(UUID.randomUUID()));

        scheduler(paymentService, new PaymentVerificationProperties()).sweepAbandonedInitiations();

        verify(paymentService, never()).findDueVerificationAttemptIds();
        verify(paymentService, never()).reconcileAttempt(any());
    }

    @Test
    void theReconciliationScanNeverReachesIntoTheSweep() {
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.findDueVerificationAttemptIds()).thenReturn(List.of(UUID.randomUUID()));

        scheduler(paymentService, new PaymentVerificationProperties()).reconcileDueAttempts();

        verify(paymentService, never()).findStuckInitiationIds();
        verify(paymentService, never()).abandonStuckInitiation(any());
    }

    @Test
    void aFailingSweepStillLetsTheReconciliationScanRun() {
        PaymentService paymentService = mock(PaymentService.class);
        UUID stuck = UUID.randomUUID();
        UUID due = UUID.randomUUID();
        when(paymentService.findStuckInitiationIds()).thenReturn(List.of(stuck));
        when(paymentService.abandonStuckInitiation(stuck)).thenThrow(new IllegalStateException("database down"));
        when(paymentService.findDueVerificationAttemptIds()).thenReturn(List.of(due));

        PaymentVerificationScheduler scheduler = scheduler(paymentService, new PaymentVerificationProperties());
        scheduler.sweepAbandonedInitiations();
        scheduler.reconcileDueAttempts();

        verify(paymentService).reconcileAttempt(due);
    }

    @Test
    void theKillSwitchPreventsAnyVerificationCall() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentVerificationProperties properties = new PaymentVerificationProperties();
        properties.setEnabled(false);

        PaymentVerificationScheduler scheduler = scheduler(paymentService, properties);
        scheduler.sweepAbandonedInitiations();
        scheduler.reconcileDueAttempts();

        verifyNoInteractions(paymentService);
    }

    private static PaymentVerificationScheduler scheduler(PaymentService paymentService,
                                                          PaymentVerificationProperties properties) {
        return new PaymentVerificationScheduler(paymentService, properties);
    }
}

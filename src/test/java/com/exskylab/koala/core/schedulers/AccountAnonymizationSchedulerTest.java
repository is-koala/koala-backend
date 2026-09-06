package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.core.properties.AccountDeletionProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAnonymizationSchedulerTest {

    @Test
    void onlyRequestsPastTheWaitingPeriodAreProcessed() {
        LegalService legalService = mock(LegalService.class);
        AccountDeletionProperties properties = properties(true);
        UUID requestId = UUID.randomUUID();
        when(legalService.findDueDeletionRequestIds(any(), any(), any())).thenReturn(List.of(requestId));

        new AccountAnonymizationScheduler(legalService, properties).processDueRequests();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(legalService).findDueDeletionRequestIds(cutoff.capture(), any(), any(Pageable.class));
        assertTrue(cutoff.getValue().isBefore(Instant.now().minus(Duration.ofDays(29))));
        verify(legalService).processDeletionRequest(requestId);
    }

    @Test
    void aDisabledSchedulerDoesNotReadRequests() {
        LegalService legalService = mock(LegalService.class);

        new AccountAnonymizationScheduler(legalService, properties(false)).processDueRequests();

        verify(legalService, never()).findDueDeletionRequestIds(any(), any(), any());
    }

    private AccountDeletionProperties properties(boolean enabled) {
        AccountDeletionProperties properties = new AccountDeletionProperties();
        properties.setEnabled(enabled);
        properties.setWaitingPeriod(Duration.ofDays(30));
        properties.setBatchSize(25);
        return properties;
    }
}

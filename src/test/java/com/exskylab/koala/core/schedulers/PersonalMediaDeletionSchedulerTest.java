package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.core.privacy.PersonalMediaDeletionOutbox;
import com.exskylab.koala.core.properties.AccountDeletionProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PersonalMediaDeletionSchedulerTest {

    @Test
    void anEnabledSchedulerRetriesTheConfiguredBatch() {
        PersonalMediaDeletionOutbox outbox = mock(PersonalMediaDeletionOutbox.class);
        AccountDeletionProperties properties = new AccountDeletionProperties();
        properties.setBatchSize(25);

        new PersonalMediaDeletionScheduler(outbox, properties).retryPendingDeletes();

        verify(outbox).processPending(25);
    }

    @Test
    void aDisabledSchedulerLeavesTheOutboxUntouched() {
        PersonalMediaDeletionOutbox outbox = mock(PersonalMediaDeletionOutbox.class);
        AccountDeletionProperties properties = new AccountDeletionProperties();
        properties.setEnabled(false);

        new PersonalMediaDeletionScheduler(outbox, properties).retryPendingDeletes();

        verify(outbox, never()).processPending(100);
    }
}

package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileSyncSchedulerTest {

    @Test
    void anInfrastructureFailureRemainsObservableToTheSchedulerRuntime() {
        UUID id = UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("database unavailable");
        UserProfileSyncService service = mock(UserProfileSyncService.class);
        when(service.findDueTaskIds()).thenReturn(List.of(id));
        when(service.runTask(id)).thenThrow(failure);

        UserProfileSyncScheduler scheduler = new UserProfileSyncScheduler(service);

        assertSame(failure, assertThrows(RuntimeException.class, scheduler::processDueTasks));
    }

    @Test
    void dueIntentsAreDispatchedInTheOrderReturnedByTheOutbox() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UserProfileSyncService service = mock(UserProfileSyncService.class);
        when(service.findDueTaskIds()).thenReturn(List.of(first, second));

        new UserProfileSyncScheduler(service).processDueTasks();

        verify(service).runTask(first);
        verify(service).runTask(second);
    }
}

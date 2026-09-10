package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchSchedulerTest {

    @Test
    void anInfrastructureFailureRemainsObservableToTheSchedulerRuntime() {
        UUID id = UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("database unavailable");
        NotificationDispatchService service = mock(NotificationDispatchService.class);
        when(service.findDueTaskIds()).thenReturn(List.of(id));
        when(service.runTask(id)).thenThrow(failure);

        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(service, Runnable::run);

        assertSame(failure, assertThrows(RuntimeException.class, scheduler::processDueTasks));
    }

    @Test
    void dueNotificationsAreSubmittedIndividuallyToTheDispatchExecutor() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        NotificationDispatchService service = mock(NotificationDispatchService.class);
        when(service.findDueTaskIds()).thenReturn(List.of(first, second));
        List<Runnable> submitted = new ArrayList<>();
        NotificationDispatchScheduler scheduler = new NotificationDispatchScheduler(service, submitted::add);

        scheduler.processDueTasks();

        verify(service).findDueTaskIds();
        verify(service, never()).runTask(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals(2, submitted.size());
        submitted.forEach(Runnable::run);
        verify(service).runTask(first);
        verify(service).runTask(second);
    }
}

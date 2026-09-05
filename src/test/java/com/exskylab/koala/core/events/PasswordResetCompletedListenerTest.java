package com.exskylab.koala.core.events;

import com.exskylab.koala.business.abstracts.PasswordResetNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordResetCompletedListenerTest {

    @Test
    void notificationRunsOnlyAfterThePasswordTransactionCommits() throws Exception {
        PasswordResetNotificationService notifications = mock(PasswordResetNotificationService.class);
        PasswordResetCompletedListener listener = new PasswordResetCompletedListener(notifications);
        PasswordResetCompletedEvent event = new PasswordResetCompletedEvent(UUID.randomUUID());

        listener.onPasswordResetCompleted(event);

        verify(notifications).sendCompletionEmail(event.userId());
        Method method = PasswordResetCompletedListener.class
                .getMethod("onPasswordResetCompleted", PasswordResetCompletedEvent.class);
        assertEquals(TransactionPhase.AFTER_COMMIT,
                method.getAnnotation(TransactionalEventListener.class).phase());
    }

    @Test
    void notificationFailureCannotEscapeAfterThePasswordWasCommitted() {
        PasswordResetNotificationService notifications = mock(PasswordResetNotificationService.class);
        PasswordResetCompletedListener listener = new PasswordResetCompletedListener(notifications);
        PasswordResetCompletedEvent event = new PasswordResetCompletedEvent(UUID.randomUUID());
        doThrow(new IllegalStateException("queue unavailable"))
                .when(notifications).sendCompletionEmail(event.userId());

        assertDoesNotThrow(() -> listener.onPasswordResetCompleted(event));
    }
}

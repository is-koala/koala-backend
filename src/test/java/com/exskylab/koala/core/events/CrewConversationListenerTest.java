package com.exskylab.koala.core.events;

import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.concretes.MessagingManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CrewConversationListenerTest {

    private final MessagingService messagingService = mock(MessagingService.class);
    private final CrewConversationListener listener = new CrewConversationListener(messagingService);

    private final UUID jobId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @Test
    void aPaidAssignmentPutsTheWorkerIntoTheCrew() {
        listener.onAssignmentPaid(new AssignmentPaidEvent(jobId, workerId, assignmentId));

        verify(messagingService).joinJobCrew(jobId, workerId);
    }

    @Test
    void aClosedAssignmentTakesTheWorkerOutOfTheCrew() {
        listener.onAssignmentClosed(new AssignmentClosedEvent(jobId, workerId, assignmentId));

        verify(messagingService).leaveJobCrew(jobId, workerId);
    }

    @Test
    void aNewCompanyContactJoinsTheCompanyCrews() {
        listener.onCompanyContactAdded(new CompanyContactAddedEvent(companyId, workerId));

        verify(messagingService).joinCompanyCrews(companyId, workerId);
    }

    @Test
    void aRemovedCompanyContactLeavesTheCompanyCrews() {
        listener.onCompanyContactRemoved(new CompanyContactRemovedEvent(companyId, workerId));

        verify(messagingService).leaveCompanyCrews(companyId, workerId);
    }

    @Test
    void aFailingCrewUpdateNeverEscapesToThePublisher() {
        doThrow(new IllegalStateException("crew broke")).when(messagingService).joinJobCrew(jobId, workerId);
        doThrow(new IllegalStateException("crew broke")).when(messagingService).leaveJobCrew(jobId, workerId);

        assertDoesNotThrow(() -> listener.onAssignmentPaid(new AssignmentPaidEvent(jobId, workerId, assignmentId)));
        assertDoesNotThrow(() -> listener.onAssignmentClosed(new AssignmentClosedEvent(jobId, workerId, assignmentId)));
    }

    @Test
    void everyCrewListenerRunsAfterThePublishersTransactionCommitted() {
        for (Method handler : handlers()) {
            TransactionalEventListener annotation = handler.getAnnotation(TransactionalEventListener.class);
            assertNotNull(annotation, handler.getName() + " must be a transactional event listener");
            assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(),
                    handler.getName() + " must wait for the publisher's commit");
            assertTrue(annotation.fallbackExecution(),
                    handler.getName() + " must still run when the publisher has no transaction");
        }
    }

    @Test
    void everyCrewMutationOpensItsOwnTransactionBecauseThePublishersIsAlreadyCommitted()
            throws NoSuchMethodException {
        for (String name : List.of("joinJobCrew", "leaveJobCrew", "joinCompanyCrews", "leaveCompanyCrews")) {
            Transactional transactional = MessagingManager.class
                    .getMethod(name, UUID.class, UUID.class)
                    .getAnnotation(Transactional.class);
            assertNotNull(transactional, name + " must be transactional");
            assertEquals(Propagation.REQUIRES_NEW, transactional.propagation(),
                    name + " would silently write into an already committed transaction without REQUIRES_NEW");
        }
    }

    private static List<Method> handlers() {
        List<Method> handlers = new java.util.ArrayList<>();
        for (Method method : CrewConversationListener.class.getDeclaredMethods()) {
            if (method.getName().startsWith("on")) {
                handlers.add(method);
            }
        }
        assertEquals(4, handlers.size(), "the listener is expected to handle four crew events");
        return handlers;
    }
}

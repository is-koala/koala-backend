package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessagingAuthorizationTest {

    private ConversationDao conversationDao;
    private MessageDao messageDao;
    private RealtimePublisher realtimePublisher;
    private UserBlockDao userBlockDao;
    private JobService jobService;
    private JobApplicationService jobApplicationService;
    private SecurityService securityService;
    private MessagingManager manager;

    private final User employer = user();
    private final User worker = user();
    private final User stranger = user();
    private Job job;

    @BeforeEach
    void setUp() {
        conversationDao = mock(ConversationDao.class);
        messageDao = mock(MessageDao.class);
        realtimePublisher = mock(RealtimePublisher.class);
        userBlockDao = mock(UserBlockDao.class);
        jobService = mock(JobService.class);
        jobApplicationService = mock(JobApplicationService.class);
        securityService = mock(SecurityService.class);
        UserService userService = mock(UserService.class);

        manager = new MessagingManager(conversationDao, messageDao, userBlockDao, jobService,
                jobApplicationService, userService, securityService, mock(CompanyContactService.class),
                new PageableSanitizer(), realtimePublisher,
                mock(com.exskylab.koala.core.mappers.MessagingMapper.class),
                new com.exskylab.koala.core.properties.MessagingProperties(),
                mock(com.exskylab.koala.core.mappers.UserMapper.class));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Garson");
        job.setCreator(employer);

        when(jobService.getById(job.getId())).thenReturn(job);
        when(conversationDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(messageDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userService.getReference(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private Conversation conversation() {
        Conversation c = new Conversation();
        c.setId(UUID.randomUUID());
        c.setJob(job);
        c.setEmployer(employer);
        c.setWorker(worker);
        c.addParticipant(employer);
        c.addParticipant(worker);
        when(conversationDao.findWithParticipantsById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void aConversationNeedsAnApplicationBehindIt() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobApplicationService.hasApplied(job.getId(), worker.getId())).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));
        verify(conversationDao, never()).save(any());
    }

    @Test
    void anApplicantCanOpenAConversationWithTheEmployer() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobApplicationService.hasApplied(job.getId(), worker.getId())).thenReturn(true);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                com.exskylab.koala.entities.ConversationKind.DIRECT)).thenReturn(Optional.empty());

        Conversation opened = manager.openConversation(job.getId(), worker.getId());

        assertEquals(employer.getId(), opened.getEmployer().getId());
        assertEquals(worker.getId(), opened.getWorker().getId());
    }

    @Test
    void openingIsIdempotentForTheSameJobAndWorker() {
        Conversation existing = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobApplicationService.hasApplied(job.getId(), worker.getId())).thenReturn(true);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                com.exskylab.koala.entities.ConversationKind.DIRECT)).thenReturn(Optional.of(existing));

        assertSame(existing, manager.openConversation(job.getId(), worker.getId()));
        verify(conversationDao, never()).save(any());
    }

    @Test
    void aStrangerCannotOpenAConversationOnSomeoneElsesJob() {
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void aStrangerCannotReadOrWriteInAConversation() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class, () -> manager.sendMessage(c.getId(), "merhaba"));
        assertThrows(ResourceNotFoundException.class,
                () -> manager.getMessages(c.getId(), org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    @Test
    void aParticipantCanSendAMessageAndItBumpsTheConversation() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        Message sent = manager.sendMessage(c.getId(), "Musaitim");

        assertEquals("Musaitim", sent.getBody());
        assertNotNull(sent.getSentAt());
        assertEquals(sent.getSentAt(), c.getLastMessageAt());
    }

    @Test
    void aBlockedUserCannotSendAMessageInEitherDirection() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(userBlockDao.existsByBlockerIdAndBlockedId(employer.getId(), worker.getId())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> manager.sendMessage(c.getId(), "merhaba"));

        reset(userBlockDao);
        when(userBlockDao.existsByBlockerIdAndBlockedId(worker.getId(), employer.getId())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> manager.sendMessage(c.getId(), "merhaba"));
    }

    @Test
    void blockingYourselfIsRejected() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ValidationException.class, () -> manager.block(worker.getId(), null));
    }

    @Test
    void blockingTwiceDoesNotCreateASecondRecord() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(userBlockDao.findByBlockerIdAndBlockedId(worker.getId(), employer.getId()))
                .thenReturn(Optional.of(new UserBlock()));

        manager.block(employer.getId(), "rahatsiz edici");

        verify(userBlockDao, never()).save(any());
    }

    @Test
    void markingReadOnlyTouchesTheCounterpartsMessages() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        manager.markRead(c.getId());

        verify(messageDao).markConversationRead(eq(c.getId()), eq(worker.getId()), any());
    }

    @Test
    void aSentMessageIsCopiedOntoTheConversationSoTheListNeedsNoExtraQuery() {
        Conversation conversation = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        manager.sendMessage(conversation.getId(), "yarın 09:00'da oradayım");

        assertEquals("yarın 09:00'da oradayım", conversation.getLastMessageBody());
        assertNotNull(conversation.getLastMessageAt());
    }

    @Test
    void unreadCountsForAWholePageComeFromOneQuery() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(messageDao.countUnreadByConversationIds(List.of(first, second), worker.getId()))
                .thenReturn(List.<Object[]>of(new Object[]{first, 3L}));

        var counts = manager.unreadCounts(List.of(first, second));

        assertEquals(3L, counts.get(first));
        assertNull(counts.get(second));
        verify(messageDao, times(1)).countUnreadByConversationIds(any(), any());
    }

    @Test
    void anEmptyPageAsksTheDatabaseNothing() {
        assertTrue(manager.unreadCounts(List.of()).isEmpty());
        verify(messageDao, never()).countUnreadByConversationIds(any(), any());
    }
}

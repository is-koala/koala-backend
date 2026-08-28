package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.dataAccess.ConversationDao;
import com.exskylab.koala.dataAccess.MessageDao;
import com.exskylab.koala.dataAccess.UserBlockDao;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.ConversationKind;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrewConversationRulesTest {

    private ConversationDao conversationDao;
    private MessageDao messageDao;
    private UserBlockDao userBlockDao;
    private SecurityService securityService;
    private MessagingProperties properties;
    private MessagingManager manager;

    private final User employer = user();
    private final User firstWorker = user();
    private final User secondWorker = user();
    private Job job;

    @BeforeEach
    void setUp() {
        conversationDao = mock(ConversationDao.class);
        messageDao = mock(MessageDao.class);
        userBlockDao = mock(UserBlockDao.class);
        securityService = mock(SecurityService.class);
        properties = new MessagingProperties();

        JobService jobService = mock(JobService.class);
        UserService userService = mock(UserService.class);

        manager = new MessagingManager(conversationDao, messageDao, userBlockDao, jobService,
                mock(JobApplicationService.class), userService, securityService,
                mock(CompanyContactService.class), new PageableSanitizer(), mock(RealtimePublisher.class),
                mock(MessagingMapper.class), properties, mock(UserMapper.class));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Garson");
        job.setCreator(employer);
        job.setEndDateTime(LocalDateTime.now().plusDays(1));

        when(jobService.getById(job.getId())).thenReturn(job);
        when(userService.getReference(firstWorker.getId())).thenReturn(firstWorker);
        when(userService.getReference(secondWorker.getId())).thenReturn(secondWorker);
        when(conversationDao.save(any())).thenAnswer(call -> call.getArgument(0));
        when(messageDao.save(any())).thenAnswer(call -> {
            Message message = call.getArgument(0);
            message.setId(UUID.randomUUID());
            return message;
        });
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.empty());
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void theFirstHireCreatesTheCrewWithTheEmployerInIt() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());

        assertEquals(ConversationKind.GROUP, crew.getKind());
        assertTrue(crew.hasParticipant(employer.getId()));
        assertTrue(crew.hasParticipant(firstWorker.getId()));
        assertNull(crew.getWorker());
    }

    @Test
    void theSecondHireJoinsTheSameCrew() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.of(crew));

        Conversation again = manager.joinJobCrew(job.getId(), secondWorker.getId());

        assertSame(crew, again);
        assertEquals(3, again.getParticipants().size());
    }

    @Test
    void joiningTwiceDoesNotDuplicateTheMember() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.of(crew));

        manager.joinJobCrew(job.getId(), firstWorker.getId());

        assertEquals(2, crew.getParticipants().size());
    }

    @Test
    void blockingDoesNotApplyInsideTheCrew() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        crew.setId(UUID.randomUUID());
        crew.addParticipant(secondWorker);

        when(conversationDao.findWithParticipantsById(crew.getId())).thenReturn(Optional.of(crew));
        when(securityService.getAuthenticatedUserId()).thenReturn(firstWorker.getId());

        assertDoesNotThrow(() -> manager.sendMessage(crew.getId(), "merhaba ekip"));
        verifyNoInteractions(userBlockDao);
    }

    @Test
    void messagingClosesAfterTheGracePeriod() {
        properties.setGraceDaysAfterJob(7);
        job.setEndDateTime(LocalDateTime.now().minusDays(8));

        assertTrue(manager.isMessagingClosed(job));
    }

    @Test
    void messagingStaysOpenDuringTheGracePeriod() {
        properties.setGraceDaysAfterJob(7);
        job.setEndDateTime(LocalDateTime.now().minusDays(3));

        assertFalse(manager.isMessagingClosed(job));
    }

    @Test
    void aJobWithoutAnEndDateNeverCloses() {
        job.setEndDateTime(null);

        assertFalse(manager.isMessagingClosed(job));
    }

    @Test
    void sendingAfterTheWindowIsRefused() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        crew.setId(UUID.randomUUID());
        job.setEndDateTime(LocalDateTime.now().minusDays(30));

        when(conversationDao.findWithParticipantsById(crew.getId())).thenReturn(Optional.of(crew));
        when(securityService.getAuthenticatedUserId()).thenReturn(firstWorker.getId());

        assertThrows(ConflictException.class, () -> manager.sendMessage(crew.getId(), "hala buradayim"));
    }
}

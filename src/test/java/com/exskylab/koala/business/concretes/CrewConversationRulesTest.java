package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.dataAccess.ConversationDao;
import com.exskylab.koala.dataAccess.MessageDao;
import com.exskylab.koala.dataAccess.UserBlockDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.Conversation;
import com.exskylab.koala.entities.ConversationKind;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.Message;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
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
    private CompanyContactService companyContactService;
    private MessagingProperties properties;
    private MessagingAvailability availability;
    private MessagingManager manager;

    private final User employer = user();
    private final User officer = user();
    private final User firstWorker = user();
    private final User secondWorker = user();
    private Job job;
    private Company company;

    @BeforeEach
    void setUp() {
        conversationDao = mock(ConversationDao.class);
        messageDao = mock(MessageDao.class);
        userBlockDao = mock(UserBlockDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);
        properties = new MessagingProperties();
        availability = new MessagingAvailability(properties);

        JobService jobService = mock(JobService.class);
        UserService userService = mock(UserService.class);

        manager = new MessagingManager(conversationDao, messageDao, userBlockDao, jobService,
                mock(JobAssignmentService.class), userService, securityService,
                companyContactService, new PageableSanitizer(), mock(RealtimePublisher.class),
                mock(MessagingMapper.class), availability, mock(UserMapper.class));

        company = new Company();
        company.setId(UUID.randomUUID());

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Garson");
        job.setCreator(employer);
        job.setEndDateTime(LocalDateTime.now(properties.zone()).plusDays(1));

        when(jobService.getById(job.getId())).thenReturn(job);
        when(userService.getReference(firstWorker.getId())).thenReturn(firstWorker);
        when(userService.getReference(secondWorker.getId())).thenReturn(secondWorker);
        when(userService.getReference(officer.getId())).thenReturn(officer);
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

    private CompanyContact contactOf(User user) {
        CompanyContact contact = new CompanyContact();
        contact.setId(UUID.randomUUID());
        contact.setCompany(company);
        contact.setUser(user);
        return contact;
    }

    private void jobBelongsToTheCompanyWithOfficers(User... officers) {
        job.setCompany(company);
        when(companyContactService.getContactsOfCompany(company.getId()))
                .thenReturn(java.util.Arrays.stream(officers).map(this::contactOf).toList());
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
    void aCompanyJobCrewStartsWithEveryCurrentOfficer() {
        jobBelongsToTheCompanyWithOfficers(employer, officer);

        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());

        assertTrue(crew.hasParticipant(employer.getId()));
        assertTrue(crew.hasParticipant(officer.getId()));
        assertTrue(crew.hasParticipant(firstWorker.getId()));
        assertEquals(3, crew.getParticipants().size());
    }

    @Test
    void anIndividualJobCrewNeverAsksForCompanyOfficers() {
        manager.joinJobCrew(job.getId(), firstWorker.getId());

        verifyNoInteractions(companyContactService);
    }

    @Test
    void aClosedAssignmentTakesTheWorkerOutOfTheCrew() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        crew.addParticipant(secondWorker);
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.of(crew));

        manager.leaveJobCrew(job.getId(), firstWorker.getId());

        assertFalse(crew.hasParticipant(firstWorker.getId()));
        assertTrue(crew.hasParticipant(secondWorker.getId()));
        assertTrue(crew.hasParticipant(employer.getId()));
    }

    @Test
    void leavingACrewYouAreNotInOrThatDoesNotExistIsHarmless() {
        assertDoesNotThrow(() -> manager.leaveJobCrew(job.getId(), firstWorker.getId()));

        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.of(crew));
        clearInvocations(conversationDao);

        manager.leaveJobCrew(job.getId(), secondWorker.getId());

        verify(conversationDao, never()).save(any());
        assertEquals(2, crew.getParticipants().size());
    }

    @Test
    void theEmployerIsNeverRemovedFromTheCrew() {
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        when(conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP))
                .thenReturn(Optional.of(crew));

        manager.leaveJobCrew(job.getId(), employer.getId());
        manager.leaveCompanyCrews(company.getId(), employer.getId());

        assertTrue(crew.hasParticipant(employer.getId()));
    }

    @Test
    void aNewOfficerJoinsTheOpenCrewsOfTheCompany() {
        jobBelongsToTheCompanyWithOfficers(employer);
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        when(conversationDao.findAllByJobCompanyIdAndKind(company.getId(), ConversationKind.GROUP))
                .thenReturn(List.of(crew));

        manager.joinCompanyCrews(company.getId(), officer.getId());

        assertTrue(crew.hasParticipant(officer.getId()));
    }

    @Test
    void aNewOfficerDoesNotJoinACrewWhoseWindowHasClosed() {
        jobBelongsToTheCompanyWithOfficers(employer);
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        job.setEndDateTime(LocalDateTime.now(properties.zone()).minusDays(30));
        when(conversationDao.findAllByJobCompanyIdAndKind(company.getId(), ConversationKind.GROUP))
                .thenReturn(List.of(crew));

        manager.joinCompanyCrews(company.getId(), officer.getId());

        assertFalse(crew.hasParticipant(officer.getId()));
    }

    @Test
    void aRemovedOfficerLeavesEveryCrewOfTheCompany() {
        jobBelongsToTheCompanyWithOfficers(employer, officer);
        Conversation crew = manager.joinJobCrew(job.getId(), firstWorker.getId());
        job.setEndDateTime(LocalDateTime.now(properties.zone()).minusDays(30));
        when(conversationDao.findAllByJobCompanyIdAndKind(company.getId(), ConversationKind.GROUP))
                .thenReturn(List.of(crew));

        manager.leaveCompanyCrews(company.getId(), officer.getId());

        assertFalse(crew.hasParticipant(officer.getId()));
        assertTrue(crew.hasParticipant(firstWorker.getId()));
    }

    @Test
    void messagingClosesAfterTheGracePeriod() {
        properties.setGraceDaysAfterJob(7);
        job.setEndDateTime(LocalDateTime.now().minusDays(8));

        assertTrue(availability.isWindowClosed(job));
    }

    @Test
    void messagingStaysOpenDuringTheGracePeriod() {
        properties.setGraceDaysAfterJob(7);
        job.setEndDateTime(LocalDateTime.now().minusDays(3));

        assertFalse(availability.isWindowClosed(job));
    }

    @Test
    void aJobWithoutAnEndDateNeverCloses() {
        job.setEndDateTime(null);

        assertFalse(availability.isWindowClosed(job));
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

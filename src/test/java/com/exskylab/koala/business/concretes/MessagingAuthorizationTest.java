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
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.KoalaException;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.properties.MessagingProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessagingAuthorizationTest {

    private ConversationDao conversationDao;
    private MessageDao messageDao;
    private RealtimePublisher realtimePublisher;
    private UserBlockDao userBlockDao;
    private JobService jobService;
    private JobAssignmentService jobAssignmentService;
    private SecurityService securityService;
    private MessagingProperties properties;
    private com.exskylab.koala.core.mappers.MessagingMapper messagingMapper;
    private CompanyContactService companyContactService;
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
        jobAssignmentService = mock(JobAssignmentService.class);
        securityService = mock(SecurityService.class);
        properties = new MessagingProperties();
        UserService userService = mock(UserService.class);

        messagingMapper = mock(com.exskylab.koala.core.mappers.MessagingMapper.class);
        companyContactService = mock(CompanyContactService.class);
        manager = new MessagingManager(conversationDao, messageDao, userBlockDao, jobService,
                jobAssignmentService, userService, securityService, companyContactService,
                new PageableSanitizer(), realtimePublisher, messagingMapper,
                new MessagingAvailability(properties),
                mock(com.exskylab.koala.core.mappers.UserMapper.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Garson");
        job.setCreator(employer);
        job.setEndDateTime(LocalDateTime.now(properties.zone()).plusDays(1));

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

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(worker);
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        assignment.setAssignmentDateTime(Instant.now());
        when(jobAssignmentService.findLatestByJobAndWorker(job.getId(), worker.getId()))
                .thenReturn(Optional.of(assignment));
        return assignment;
    }

    private static String errorCodeOf(KoalaException exception) {
        return exception.getErrorCode();
    }

    @Test
    void aConversationNeedsAPaidAssignmentBehindIt() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobAssignmentService.findLatestByJobAndWorker(job.getId(), worker.getId()))
                .thenReturn(Optional.empty());

        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));

        assertEquals("MESSAGING_PAYMENT_REQUIRED", errorCodeOf(refused));
        verify(conversationDao, never()).save(any());
    }

    @Test
    void anAcceptedButUnpaidAssignmentStillDoesNotOpenTheConversation() {
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));

        assertEquals("MESSAGING_PAYMENT_REQUIRED", errorCodeOf(refused));
        verify(conversationDao, never()).save(any());
    }

    @Test
    void aFailedPaymentAttemptDoesNotOpenTheConversationEither() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.FAILED);

        assertThrows(ForbiddenException.class, () -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void escrowOpensTheConversationForTheWorker() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                ConversationKind.DIRECT)).thenReturn(Optional.empty());

        Conversation opened = manager.openConversation(job.getId(), worker.getId());

        assertEquals(employer.getId(), opened.getEmployer().getId());
        assertEquals(worker.getId(), opened.getWorker().getId());
        assertEquals(ConversationKind.DIRECT, opened.getKind());
    }

    @Test
    void escrowOpensTheConversationForTheEmployerToo() {
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                ConversationKind.DIRECT)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void moneyReleasedToTheWorkerKeepsTheConversationOpen() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.COMPLETED, PaymentStatus.RELEASED_TO_WORKER);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                ConversationKind.DIRECT)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void aCancelledAssignmentClosesTheConversationRatherThanAskingForPayment() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CANCELLED, PaymentStatus.HELD_IN_ESCROW);

        ConflictException closed = assertThrows(ConflictException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));

        assertEquals("MESSAGING_WINDOW_CLOSED", errorCodeOf(closed));
    }

    @Test
    void aRefundedAssignmentClosesTheConversation() {
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        assignment(AssignmentStatus.CANCELLED, PaymentStatus.REFUNDED);

        assertThrows(ConflictException.class, () -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void aRejectedAssignmentClosesTheConversation() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.REJECTED, null);

        assertThrows(ConflictException.class, () -> manager.openConversation(job.getId(), worker.getId()));
    }

    @Test
    void thePaymentGateIsAskedBeforeTheBlockList() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobAssignmentService.findLatestByJobAndWorker(job.getId(), worker.getId()))
                .thenReturn(Optional.empty());
        when(userBlockDao.existsByBlockerIdAndBlockedId(any(), any())).thenReturn(true);

        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> manager.openConversation(job.getId(), worker.getId()));

        assertEquals("MESSAGING_PAYMENT_REQUIRED", errorCodeOf(refused));
        verifyNoInteractions(userBlockDao);
    }

    @Test
    void openingIsIdempotentForTheSameJobAndWorker() {
        Conversation existing = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        when(conversationDao.findByJobIdAndWorkerIdAndKind(job.getId(), worker.getId(),
                ConversationKind.DIRECT)).thenReturn(Optional.of(existing));

        assertSame(existing, manager.openConversation(job.getId(), worker.getId()));
        verify(conversationDao, never()).save(any());
    }

    @Test
    void sendingInADirectConversationAsksThePaymentGateAgain() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> manager.sendMessage(c.getId(), "odeme oncesi"));

        assertEquals("MESSAGING_PAYMENT_REQUIRED", errorCodeOf(refused));
        verify(messageDao, never()).save(any());
    }

    @Test
    void sendingAfterTheAssignmentWasCancelledIsRefusedAsClosed() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        assignment(AssignmentStatus.CANCELLED, PaymentStatus.REFUNDED);

        ConflictException closed = assertThrows(ConflictException.class,
                () -> manager.sendMessage(c.getId(), "iptalden sonra"));

        assertEquals("MESSAGING_WINDOW_CLOSED", errorCodeOf(closed));
        verify(messageDao, never()).save(any());
    }

    @Test
    void theConversationListResolvesEveryDirectChatsLatestAssignmentWithOneBatchQuery() {
        Job otherJob = new Job();
        otherJob.setId(UUID.randomUUID());
        otherJob.setCreator(employer);
        otherJob.setEndDateTime(LocalDateTime.now(properties.zone()).plusDays(1));
        User otherWorker = user();

        Conversation paid = directConversation(job, worker);
        Conversation unpaid = directConversation(otherJob, worker);
        Conversation cancelled = directConversation(otherJob, otherWorker);
        Conversation crew = new Conversation();
        crew.setId(UUID.randomUUID());
        crew.setJob(job);
        crew.setEmployer(employer);
        crew.setKind(ConversationKind.GROUP);
        crew.addParticipant(employer);

        JobAssignment paidAssignment = assignmentOf(job, worker, AssignmentStatus.CONFIRMED,
                PaymentStatus.HELD_IN_ESCROW);
        JobAssignment cancelledAssignment = assignmentOf(otherJob, otherWorker, AssignmentStatus.CANCELLED,
                PaymentStatus.REFUNDED);

        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(conversationDao.findAllForParticipant(eq(employer.getId()), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(paid, unpaid, cancelled, crew)));
        when(messageDao.countUnreadByConversationIds(any(), any())).thenReturn(List.of());
        when(jobAssignmentService.findLatestByJobsAndWorkers(any(), any()))
                .thenReturn(List.of(paidAssignment, cancelledAssignment));

        manager.getMyConversations(org.springframework.data.domain.PageRequest.of(0, 20));

        verify(jobAssignmentService, times(1)).findLatestByJobsAndWorkers(
                java.util.Set.of(job.getId(), otherJob.getId()), java.util.Set.of(worker.getId(), otherWorker.getId()));
        verify(jobAssignmentService, never()).findLatestByJobAndWorker(any(), any());
        org.mockito.ArgumentCaptor<Boolean> closed = org.mockito.ArgumentCaptor.forClass(Boolean.class);
        org.mockito.ArgumentCaptor<Conversation> listed = org.mockito.ArgumentCaptor.forClass(Conversation.class);
        verify(messagingMapper, times(4)).toConversationDto(listed.capture(), anyLong(), closed.capture(), any());
        java.util.Map<UUID, Boolean> closedById = new java.util.HashMap<>();
        for (int i = 0; i < listed.getAllValues().size(); i++) {
            closedById.put(listed.getAllValues().get(i).getId(), closed.getAllValues().get(i));
        }
        assertFalse(closedById.get(paid.getId()), "ödenmiş atamanın sohbeti açık");
        assertTrue(closedById.get(unpaid.getId()), "arkasında atama olmayan sohbet kapalı");
        assertTrue(closedById.get(cancelled.getId()), "iptal edilen atamanın sohbeti kapalı");
        assertFalse(closedById.get(crew.getId()), "grup yalnız pencereyle kapanır");
    }

    private Conversation directConversation(Job forJob, User forWorker) {
        Conversation c = new Conversation();
        c.setId(UUID.randomUUID());
        c.setJob(forJob);
        c.setEmployer(employer);
        c.setWorker(forWorker);
        c.setKind(ConversationKind.DIRECT);
        c.addParticipant(employer);
        c.addParticipant(forWorker);
        return c;
    }

    private static JobAssignment assignmentOf(Job forJob, User forWorker, AssignmentStatus status,
                                              PaymentStatus paymentStatus) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(forJob);
        assignment.setWorker(forWorker);
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        assignment.setAssignmentDateTime(Instant.now());
        return assignment;
    }

    private Conversation crewWith(User... members) {
        Conversation crew = new Conversation();
        crew.setId(UUID.randomUUID());
        crew.setJob(job);
        crew.setEmployer(employer);
        crew.setKind(ConversationKind.GROUP);
        crew.addParticipant(employer);
        for (User member : members) {
            crew.addParticipant(member);
        }
        when(conversationDao.findWithParticipantsById(crew.getId())).thenReturn(Optional.of(crew));
        return crew;
    }

    @Test
    void aRefundedWorkerStillListedInTheCrewCannotWriteThereAnyMore() {
        Conversation crew = crewWith(worker);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CANCELLED, PaymentStatus.REFUNDED);

        ConflictException closed = assertThrows(ConflictException.class,
                () -> manager.sendMessage(crew.getId(), "iadeden sonra ekibe"));

        assertEquals("MESSAGING_WINDOW_CLOSED", errorCodeOf(closed));
        verify(messageDao, never()).save(any());
    }

    @Test
    void anUnpaidWorkerWhoSomehowSitsInTheCrewIsRefusedWithPaymentRequired() {
        Conversation crew = crewWith(worker);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        ForbiddenException refused = assertThrows(ForbiddenException.class,
                () -> manager.sendMessage(crew.getId(), "odenmeden ekibe"));

        assertEquals("MESSAGING_PAYMENT_REQUIRED", errorCodeOf(refused));
    }

    @Test
    void aPaidWorkerWritesInTheCrewAfterASingleAssignmentLookup() {
        Conversation crew = crewWith(worker);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW);

        assertDoesNotThrow(() -> manager.sendMessage(crew.getId(), "ekip merhaba"));

        verify(jobAssignmentService, times(1)).findLatestByJobAndWorker(job.getId(), worker.getId());
        verifyNoInteractions(companyContactService);
    }

    @Test
    void theEmployerWritesInTheCrewWithoutAnyAssignmentLookup() {
        Conversation crew = crewWith(worker);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertDoesNotThrow(() -> manager.sendMessage(crew.getId(), "ekibe duyuru"));

        verify(jobAssignmentService, never()).findLatestByJobAndWorker(any(), any());
    }

    @Test
    void aCompanyOfficerWritesInTheCrewWithoutAnAssignment() {
        User officer = user();
        Company company = new Company();
        company.setId(UUID.randomUUID());
        job.setCompany(company);
        Conversation crew = crewWith(worker, officer);
        when(securityService.getAuthenticatedUserId()).thenReturn(officer.getId());
        when(jobAssignmentService.findLatestByJobAndWorker(job.getId(), officer.getId()))
                .thenReturn(Optional.empty());
        when(companyContactService.isUserAContactOfCompany(company.getId(), officer.getId())).thenReturn(true);

        assertDoesNotThrow(() -> manager.sendMessage(crew.getId(), "yetkiliden"));
    }

    @Test
    void aFormerOfficerWithoutAnAssignmentCannotWriteInTheCrew() {
        User formerOfficer = user();
        Company company = new Company();
        company.setId(UUID.randomUUID());
        job.setCompany(company);
        Conversation crew = crewWith(worker, formerOfficer);
        when(securityService.getAuthenticatedUserId()).thenReturn(formerOfficer.getId());
        when(companyContactService.isUserAContactOfCompany(company.getId(), formerOfficer.getId()))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () -> manager.sendMessage(crew.getId(), "eski yetkiliden"));
    }

    @Test
    void aLegacyDirectConversationWithoutAnAssignmentIsReadOnly() {
        Conversation c = conversation();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobAssignmentService.findLatestByJobAndWorker(job.getId(), worker.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> manager.sendMessage(c.getId(), "eski sohbet"));
        assertDoesNotThrow(() -> manager.getMessages(c.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)));
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
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        Message sent = manager.sendMessage(c.getId(), "Musaitim");

        assertEquals("Musaitim", sent.getBody());
        assertNotNull(sent.getSentAt());
        assertEquals(sent.getSentAt(), c.getLastMessageAt());
    }

    @Test
    void aBlockedUserCannotSendAMessageInEitherDirection() {
        Conversation c = conversation();
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
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
        assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);
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

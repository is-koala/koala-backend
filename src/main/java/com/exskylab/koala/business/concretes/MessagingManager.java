package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.MessagingMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.messaging.response.ConversationDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessagingManager implements MessagingService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "lastMessageAt", "sentAt");
    private static final Sort CONVERSATION_SORT = Sort.by(Sort.Direction.DESC, "lastMessageAt");
    private static final Sort MESSAGE_SORT = Sort.by(Sort.Direction.DESC, "sentAt");
    private static final Sort MESSAGES_AFTER_SORT = Sort.by(Sort.Direction.ASC, "sentAt")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private final ConversationDao conversationDao;
    private final MessageDao messageDao;
    private final UserBlockDao userBlockDao;
    private final JobService jobService;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;
    private final PageableSanitizer pageableSanitizer;
    private final RealtimePublisher realtimePublisher;
    private final MessagingMapper messagingMapper;
    private final MessagingAvailability messagingAvailability;
    private final UserMapper userMapper;
    private final ActivityNotificationService activityNotificationService;

    public MessagingManager(ConversationDao conversationDao, MessageDao messageDao,
                            UserBlockDao userBlockDao, JobService jobService,
                            JobAssignmentService jobAssignmentService, UserService userService,
                            SecurityService securityService, CompanyContactService companyContactService,
                            PageableSanitizer pageableSanitizer, RealtimePublisher realtimePublisher,
                            MessagingMapper messagingMapper, MessagingAvailability messagingAvailability,
                            UserMapper userMapper, ActivityNotificationService activityNotificationService) {
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
        this.userBlockDao = userBlockDao;
        this.jobService = jobService;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.pageableSanitizer = pageableSanitizer;
        this.realtimePublisher = realtimePublisher;
        this.messagingMapper = messagingMapper;
        this.messagingAvailability = messagingAvailability;
        this.userMapper = userMapper;
        this.activityNotificationService = activityNotificationService;
    }

    @Override
    @Transactional
    public Conversation openConversation(UUID jobId, UUID workerId) {
        Job job = jobService.getById(jobId);

        UUID currentUserId = securityService.getAuthenticatedUserId();
        UUID resolvedWorkerId = workerId != null ? workerId : currentUserId;

        assertMayConverse(job, resolvedWorkerId, currentUserId);
        assertDirectMessagingOpen(job, resolvedWorkerId);
        assertNotBlocked(currentUserId, employerOf(job).getId().equals(currentUserId)
                ? resolvedWorkerId : employerOf(job).getId());

        return conversationDao.findByJobIdAndWorkerIdAndKind(jobId, resolvedWorkerId, ConversationKind.DIRECT)
                .orElseGet(() -> {
                    Conversation conversation = new Conversation();
                    conversation.setJob(job);
                    conversation.setKind(ConversationKind.DIRECT);
                    conversation.setEmployer(employerOf(job));
                    conversation.setWorker(userService.getReference(resolvedWorkerId));
                    conversation.addParticipant(employerOf(job));
                    conversation.addParticipant(userService.getReference(resolvedWorkerId));

                    log.info("Direct conversation opened for job {} and worker {}", jobId, resolvedWorkerId);
                    return conversationDao.save(conversation);
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation joinJobCrew(UUID jobId, UUID workerId) {
        Job job = jobService.getById(jobId);
        User worker = userService.getReference(workerId);

        Conversation crew = conversationDao.findByJobIdAndKind(job.getId(), ConversationKind.GROUP)
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setJob(job);
                    created.setKind(ConversationKind.GROUP);
                    created.setEmployer(employerOf(job));
                    created.addParticipant(employerOf(job));
                    if (job.getCompany() != null) {
                        companyContactService.getContactsOfCompany(job.getCompany().getId())
                                .forEach(contact -> created.addParticipant(contact.getUser()));
                    }

                    log.info("Crew conversation created for job {} with {} employer-side participants",
                            job.getId(), created.getParticipants().size());
                    return conversationDao.save(created);
                });

        if (!crew.hasParticipant(worker.getId())) {
            crew.addParticipant(worker);
            conversationDao.save(crew);
            log.info("Worker {} joined the crew conversation of job {}", worker.getId(), job.getId());
        }

        return crew;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void leaveJobCrew(UUID jobId, UUID workerId) {
        conversationDao.findByJobIdAndKind(jobId, ConversationKind.GROUP).ifPresent(crew -> {
            if (crew.getEmployer().getId().equals(workerId)) {
                return;
            }
            if (crew.removeParticipant(workerId)) {
                conversationDao.save(crew);
                log.info("Worker {} left the crew conversation of job {}", workerId, jobId);
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void joinCompanyCrews(UUID companyId, UUID userId) {
        User contact = userService.getReference(userId);
        int joined = 0;
        for (Conversation crew : conversationDao.findAllByJobCompanyIdAndKind(companyId, ConversationKind.GROUP)) {
            if (messagingAvailability.isWindowClosed(crew.getJob()) || crew.hasParticipant(userId)) {
                continue;
            }
            crew.addParticipant(contact);
            conversationDao.save(crew);
            joined++;
        }
        log.info("Company contact {} joined {} open crew conversations of company {}", userId, joined, companyId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void leaveCompanyCrews(UUID companyId, UUID userId) {
        int left = 0;
        for (Conversation crew : conversationDao.findAllByJobCompanyIdAndKind(companyId, ConversationKind.GROUP)) {
            if (crew.getEmployer().getId().equals(userId)) {
                continue;
            }
            if (crew.removeParticipant(userId)) {
                conversationDao.save(crew);
                left++;
            }
        }
        log.info("Company contact {} left {} crew conversations of company {}", userId, left, companyId);
    }

    @Override
    @Transactional
    public Message sendMessage(UUID conversationId, String body) {
        Conversation conversation = loadParticipating(conversationId);
        UUID senderId = securityService.getAuthenticatedUserId();

        if (conversation.isGroup()) {
            assertMessagingWindowOpen(conversation.getJob());
            assertCrewMemberMayMessage(conversation, senderId);
        } else {
            assertDirectMessagingOpen(conversation.getJob(),
                    conversation.getWorker() == null ? null : conversation.getWorker().getId());
            assertNotBlocked(senderId, conversation.counterpartOf(senderId).getId());
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(userService.getReference(senderId));
        message.setBody(body);
        message.setSentAt(Instant.now());

        Message saved = messageDao.save(message);
        conversation.setLastMessageAt(saved.getSentAt());
        conversation.setLastMessageBody(saved.getBody());
        conversationDao.save(conversation);

        activityNotificationService.messageSent(saved);

        realtimePublisher.messageSent(conversationId, messagingMapper.toMessageDto(saved),
                conversation.participantIds());

        log.info("Message {} sent in conversation {}", saved.getId(), conversationId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Message> getMessages(UUID conversationId, Pageable requested) {
        loadParticipating(conversationId);
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);
        return messageDao.findAllByConversationIdOrderBySentAtDesc(conversationId,
                pageableSanitizer.sanitize(requested, SORTABLE, MESSAGE_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Message> getMessagesAfter(UUID conversationId, UUID afterMessageId, Pageable requested) {
        loadParticipating(conversationId);
        Message anchor = messageDao.findByIdAndConversationId(afterMessageId, conversationId)
                .orElseThrow(() -> {
                    log.warn("Incremental message fetch for conversation {} refused: anchor {} is not a message "
                            + "of this conversation", conversationId, afterMessageId);
                    return new ValidationException(MessagingMessages.AFTER_UNKNOWN);
                });
        return messageDao.findAllAfter(conversationId, anchor.getSentAt(), anchor.getId(),
                pageableSanitizer.sanitize(requested, Set.of(), MESSAGES_AFTER_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDto getConversation(UUID conversationId) {
        Conversation conversation = loadParticipating(conversationId);
        return messagingMapper.toConversationDto(conversation,
                unreadCounts(List.of(conversationId)).getOrDefault(conversationId, 0L),
                closedFlags(List.of(conversation)).get(conversationId),
                membersOf(conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<ConversationDto> getMyConversations(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);

        Page<Conversation> page = conversationDao.findAllForParticipant(securityService.getAuthenticatedUserId(),
                pageableSanitizer.sanitize(requested, SORTABLE, CONVERSATION_SORT));

        Map<UUID, Long> unread = unreadCounts(page.getContent().stream().map(Conversation::getId).toList());
        Map<UUID, Boolean> closed = closedFlags(page.getContent());

        return PageDto.of(page, page.getContent().stream()
                .map(conversation -> messagingMapper.toConversationDto(conversation,
                        unread.getOrDefault(conversation.getId(), 0L),
                        closed.get(conversation.getId()),
                        membersOf(conversation)))
                .toList());
    }

    private Map<UUID, Boolean> closedFlags(List<Conversation> conversations) {
        List<Conversation> direct = conversations.stream()
                .filter(conversation -> !conversation.isGroup() && conversation.getWorker() != null)
                .toList();

        Map<Map.Entry<UUID, UUID>, JobAssignment> latestByJobAndWorker = direct.isEmpty() ? Map.of()
                : jobAssignmentService.findLatestByJobsAndWorkers(
                        direct.stream().map(conversation -> conversation.getJob().getId()).collect(Collectors.toSet()),
                        direct.stream().map(conversation -> conversation.getWorker().getId()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        assignment -> Map.entry(assignment.getJob().getId(), assignment.getWorker().getId()),
                        Function.identity()));

        return conversations.stream().collect(Collectors.toMap(Conversation::getId, conversation -> {
            if (conversation.isGroup() || conversation.getWorker() == null) {
                return messagingAvailability.isWindowClosed(conversation.getJob());
            }
            JobAssignment assignment = latestByJobAndWorker.get(
                    Map.entry(conversation.getJob().getId(), conversation.getWorker().getId()));
            return !messagingAvailability.isAvailable(assignment);
        }));
    }

    @Override
    @Transactional
    public int markRead(UUID conversationId) {
        Conversation conversation = loadParticipating(conversationId);
        UUID readerId = securityService.getAuthenticatedUserId();

        int marked = messageDao.markConversationRead(conversationId, readerId, Instant.now());
        if (marked > 0) {
            realtimePublisher.conversationRead(conversationId, readerId, conversation.participantIds());
        }
        return marked;
    }

    @Override
    @Transactional
    public UserBlock block(UUID blockedUserId, String reason) {
        UUID blockerId = securityService.getAuthenticatedUserId();

        if (blockerId.equals(blockedUserId)) {
            throw new ValidationException(MessagingMessages.CANNOT_BLOCK_SELF);
        }

        return userBlockDao.findByBlockerIdAndBlockedId(blockerId, blockedUserId)
                .orElseGet(() -> {
                    UserBlock block = new UserBlock();
                    block.setBlocker(userService.getReference(blockerId));
                    block.setBlocked(userService.getReference(blockedUserId));
                    block.setReason(reason);
                    log.info("User {} blocked user {}", blockerId, blockedUserId);
                    return userBlockDao.save(block);
                });
    }

    @Override
    @Transactional
    public void unblock(UUID blockedUserId) {
        userBlockDao.findByBlockerIdAndBlockedId(securityService.getAuthenticatedUserId(), blockedUserId)
                .ifPresent(userBlockDao::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBlock> getMyBlocks() {
        return userBlockDao.findAllByBlockerId(securityService.getAuthenticatedUserId());
    }

    private List<SafeUserDto> membersOf(Conversation conversation) {
        return conversation.getParticipants().stream()
                .map(participant -> userMapper.toSafeUserDto(participant.getUser()))
                .toList();
    }

    private void assertMessagingWindowOpen(Job job) {
        if (messagingAvailability.isWindowClosed(job)) {
            log.info("Messaging window closed for job {}, it ended at {}", job.getId(), job.getEndDateTime());
            throw new ConflictException(MessagingMessages.WINDOW_CLOSED);
        }
    }

    private void assertDirectMessagingOpen(Job job, UUID workerId) {
        refuseUnlessAvailable(job, workerId, latestAssignmentOf(job, workerId));
    }

    private void assertCrewMemberMayMessage(Conversation crew, UUID senderId) {
        if (crew.getEmployer().getId().equals(senderId)) {
            return;
        }
        Job job = crew.getJob();
        Optional<JobAssignment> assignment = latestAssignmentOf(job, senderId);
        if (assignment.isEmpty() && job.getCompany() != null
                && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), senderId)) {
            return;
        }
        refuseUnlessAvailable(job, senderId, assignment);
    }

    private Optional<JobAssignment> latestAssignmentOf(Job job, UUID workerId) {
        return workerId == null ? Optional.empty()
                : jobAssignmentService.findLatestByJobAndWorker(job.getId(), workerId);
    }

    private void refuseUnlessAvailable(Job job, UUID workerId, Optional<JobAssignment> assignment) {
        switch (messagingAvailability.reasonOf(assignment.orElse(null))) {
            case PAYMENT_REQUIRED -> {
                log.info("Messaging for job {} and worker {} refused: payment status {}", job.getId(), workerId,
                        assignment.map(JobAssignment::getPaymentStatus).orElse(null));
                throw new ForbiddenException(MessagingMessages.PAYMENT_REQUIRED);
            }
            case CLOSED -> {
                log.info("Messaging for job {} and worker {} closed: assignment {} status {}, payment {}",
                        job.getId(), workerId, assignment.map(JobAssignment::getId).orElse(null),
                        assignment.map(JobAssignment::getStatus).orElse(null),
                        assignment.map(JobAssignment::getPaymentStatus).orElse(null));
                throw new ConflictException(MessagingMessages.WINDOW_CLOSED);
            }
            case AVAILABLE -> {
            }
        }
    }

    private Conversation loadParticipating(UUID conversationId) {
        Conversation conversation = conversationDao.findWithParticipantsById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(MessagingMessages.CONVERSATION_NOT_FOUND));

        UUID actorId = securityService.getAuthenticatedUserId();
        if (!conversation.hasParticipant(actorId)) {
            throw Concealment.notFound(MessagingMessages.CONVERSATION_NOT_FOUND, actorId, conversationId,
                    "not a participant of the conversation");
        }
        return conversation;
    }

    private User employerOf(Job job) {
        return job.getCreator();
    }

    private void assertMayConverse(Job job, UUID workerId, UUID currentUserId) {
        boolean currentIsEmployerSide = employerOf(job).getId().equals(currentUserId)
                || (job.getCompany() != null
                    && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), currentUserId));

        if (!currentIsEmployerSide && !currentUserId.equals(workerId)) {
            throw new ResourceNotFoundException(MessagingMessages.NOT_A_PARTICIPANT);
        }
    }

    private void assertNotBlocked(UUID first, UUID second) {
        if (userBlockDao.existsByBlockerIdAndBlockedId(first, second)
                || userBlockDao.existsByBlockerIdAndBlockedId(second, first)) {
            throw new ForbiddenException(MessagingMessages.BLOCKED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> unreadCounts(Collection<UUID> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return messageDao.countUnreadByConversationIds(conversationIds, securityService.getAuthenticatedUserId())
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = conversationDao.findWithParticipantsById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(MessagingMessages.CONVERSATION_NOT_FOUND));

        if (!conversation.hasParticipant(userId)) {
            throw Concealment.notFound(MessagingMessages.CONVERSATION_NOT_FOUND, userId, conversationId,
                    "not a participant of the conversation");
        }
    }

    @Override
    @Transactional
    public Message getMessageForReport(UUID messageId, UUID reporterId) {
        return messageDao.findReportableByIdForUpdate(messageId, reporterId)
                .orElseThrow(() -> new ResourceNotFoundException(MessagingMessages.MESSAGE_NOT_FOUND));
    }
}

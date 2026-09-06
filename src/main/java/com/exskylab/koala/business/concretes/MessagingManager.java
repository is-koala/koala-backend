package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
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
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.core.mappers.MessagingMapper;
import com.exskylab.koala.core.mappers.UserMapper;
import com.exskylab.koala.core.realtime.RealtimePublisher;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessagingManager implements MessagingService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "lastMessageAt", "sentAt");
    private static final Sort CONVERSATION_SORT = Sort.by(Sort.Direction.DESC, "lastMessageAt");
    private static final Sort MESSAGE_SORT = Sort.by(Sort.Direction.DESC, "sentAt");

    private final ConversationDao conversationDao;
    private final MessageDao messageDao;
    private final UserBlockDao userBlockDao;
    private final JobService jobService;
    private final JobApplicationService jobApplicationService;
    private final UserService userService;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;
    private final PageableSanitizer pageableSanitizer;
    private final RealtimePublisher realtimePublisher;
    private final MessagingMapper messagingMapper;
    private final MessagingProperties messagingProperties;
    private final UserMapper userMapper;

    public MessagingManager(ConversationDao conversationDao, MessageDao messageDao,
                            UserBlockDao userBlockDao, JobService jobService,
                            JobApplicationService jobApplicationService, UserService userService,
                            SecurityService securityService, CompanyContactService companyContactService,
                            PageableSanitizer pageableSanitizer, RealtimePublisher realtimePublisher,
                            MessagingMapper messagingMapper, MessagingProperties messagingProperties,
                            UserMapper userMapper) {
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
        this.userBlockDao = userBlockDao;
        this.jobService = jobService;
        this.jobApplicationService = jobApplicationService;
        this.userService = userService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.pageableSanitizer = pageableSanitizer;
        this.realtimePublisher = realtimePublisher;
        this.messagingMapper = messagingMapper;
        this.messagingProperties = messagingProperties;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public Conversation openConversation(UUID jobId, UUID workerId) {
        Job job = jobService.getById(jobId);

        UUID currentUserId = securityService.getAuthenticatedUserId();
        UUID resolvedWorkerId = workerId != null ? workerId : currentUserId;

        assertMayConverse(job, resolvedWorkerId, currentUserId);
        assertNotBlocked(currentUserId, employerOf(job).getId().equals(currentUserId)
                ? resolvedWorkerId : employerOf(job).getId());

        assertMessagingWindowOpen(job);

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
    @Transactional
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

                    log.info("Crew conversation created for job {}", job.getId());
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
    @Transactional
    public Message sendMessage(UUID conversationId, String body) {
        Conversation conversation = loadParticipating(conversationId);
        UUID senderId = securityService.getAuthenticatedUserId();

        assertMessagingWindowOpen(conversation.getJob());

        if (!conversation.isGroup()) {
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
    public PageDto<ConversationDto> getMyConversations(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);

        Page<Conversation> page = conversationDao.findAllForParticipant(securityService.getAuthenticatedUserId(),
                pageableSanitizer.sanitize(requested, SORTABLE, CONVERSATION_SORT));

        Map<UUID, Long> unread = unreadCounts(page.getContent().stream().map(Conversation::getId).toList());

        return PageDto.of(page, page.getContent().stream()
                .map(conversation -> messagingMapper.toConversationDto(conversation,
                        unread.getOrDefault(conversation.getId(), 0L),
                        isMessagingClosed(conversation.getJob()),
                        membersOf(conversation)))
                .toList());
    }

    @Override
    public boolean isMessagingClosed(Job job) {
        if (job == null || job.getEndDateTime() == null) {
            return false;
        }

        return LocalDateTime.now(messagingProperties.zone())
                .isAfter(job.getEndDateTime().plusDays(messagingProperties.getGraceDaysAfterJob()));
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
        if (isMessagingClosed(job)) {
            log.info("Messaging window closed for job {}, it ended at {}", job.getId(), job.getEndDateTime());
            throw new ConflictException(MessagingMessages.WINDOW_CLOSED);
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

        if (!jobApplicationService.hasApplied(job.getId(), workerId)) {
            throw new ForbiddenException(MessagingMessages.NO_RELATIONSHIP_WITH_JOB);
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
    @Transactional(readOnly = true)
    public Message getMessageById(UUID messageId) {
        return messageDao.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(MessagingMessages.MESSAGE_NOT_FOUND));
    }
}

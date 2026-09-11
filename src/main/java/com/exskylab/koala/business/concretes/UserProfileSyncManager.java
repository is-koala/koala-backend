package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.helpers.UserProfileSyncBackoff;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.logging.UserProfileSyncLogContext;
import com.exskylab.koala.core.monitoring.UserProfileSyncMetrics;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.core.properties.UserProfileSyncProperties;
import com.exskylab.koala.dataAccess.UserProfileSyncOutboxDao;
import com.exskylab.koala.entities.UserProfileSyncOutbox;
import com.exskylab.koala.entities.UserProfileSyncStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserProfileSyncManager implements UserProfileSyncService {

    static final String BROKER_RETRY = "BROKER_PUBLISH_RETRY_SCHEDULED";
    static final String BROKER_EXHAUSTED = "BROKER_PUBLISH_EXHAUSTED";
    static final String INVALID_PAYLOAD = "DISPATCH_PAYLOAD_INVALID";

    private final UserProfileSyncOutboxDao outboxDao;
    private final ConfirmedRabbitPublisher publisher;
    private final UserProfileSyncProperties properties;
    private final UserProfileSyncBackoff backoff;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final UserProfileSyncMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public UserProfileSyncManager(UserProfileSyncOutboxDao outboxDao, ConfirmedRabbitPublisher publisher,
                                  UserProfileSyncProperties properties, UserProfileSyncBackoff backoff,
                                  ObjectMapper objectMapper, Clock clock, UserProfileSyncMetrics metrics,
                                  PlatformTransactionManager transactionManager) {
        this.outboxDao = outboxDao;
        this.publisher = publisher;
        this.properties = properties;
        this.backoff = backoff;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public void enqueue(UserUpdatedEventDto event) {
        Instant now = clock.instant();
        Map<String, String> context = LogContext.carried();
        Optional<UUID> existing = outboxDao.findUnclaimedUnpublishedId(event.getId());
        if (existing.isPresent()) {
            event.setIntentId(existing.get());
            String payload = writePayload(event);
            if (outboxDao.replaceUnpublishedPayload(existing.get(), payload, now,
                    context.get(LogContext.REQUEST_ID), context.get(LogContext.USER_ID),
                    context.get(LogContext.SESSION_ID), context.get(LogContext.DEVICE_ID)) == 1) {
                log.info("Replaced unpublished Iyzico profile sync intent {} for user {}", existing.get(),
                        event.getId());
                return;
            }
        }

        UUID intentId = UUID.randomUUID();
        event.setIntentId(intentId);
        UserProfileSyncOutbox outbox = new UserProfileSyncOutbox();
        outbox.setId(intentId);
        outbox.setUserId(event.getId());
        outbox.setPayload(writePayload(event));
        outbox.setStatus(UserProfileSyncStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        UserProfileSyncLogContext.capture(outbox);
        outboxDao.save(outbox);
        log.info("Accepted Iyzico profile sync intent {} for user {}", intentId, event.getId());
    }

    @Override
    public List<UUID> findDueTaskIds() {
        Instant now = clock.instant();
        if (!properties.isEnabled()) {
            refreshMetrics(now);
            return List.of();
        }
        List<UUID> result = transactionTemplate.execute(status -> {
            outboxDao.terminalizeExhaustedDispatches(properties.getMaxAttempts(), now, BROKER_EXHAUSTED);
            outboxDao.releaseStaleDispatchClaims(now.minus(properties.getClaimTimeout()), now);
            return outboxDao.findDueDispatchIds(now, properties.getMaxAttempts(),
                    PageRequest.of(0, properties.getBatchSize()));
        });
        refreshMetrics(now);
        return result == null ? List.of() : result;
    }

    @Override
    public boolean runTask(UUID intentId) {
        if (!properties.isEnabled()) {
            return false;
        }
        DispatchExecution execution = claim(intentId);
        if (execution == null) {
            return false;
        }
        try (var ignored = UserProfileSyncLogContext.activate(execution.outbox())) {
            UserUpdatedEventDto event;
            try {
                event = objectMapper.readValue(execution.payload(), UserUpdatedEventDto.class);
            } catch (JacksonException exception) {
                terminalize(intentId, execution.claimId(), INVALID_PAYLOAD);
                return false;
            }
            try {
                publisher.convertAndPublish(RabbitMQConfig.USER_EXCHANGE,
                        RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY, event,
                        properties.getPublisherConfirmTimeout());
            } catch (RuntimeException exception) {
                log.warn("Iyzico profile sync intent {} could not be published on attempt {}",
                        intentId, execution.attempts());
                fail(intentId, execution);
                return false;
            }
            return complete(intentId, execution.claimId());
        }
    }

    @Override
    public Optional<UUID> claimForProcessing(UUID intentId) {
        UUID claimId = UUID.randomUUID();
        Instant now = clock.instant();
        Instant staleBefore = now.minus(properties.getProcessingClaimTimeout());
        Integer claimed = transactionTemplate.execute(status ->
                outboxDao.claimProcessing(intentId, claimId, now, staleBefore));
        if (claimed != null && claimed == 1) {
            return Optional.of(claimId);
        }
        return Optional.empty();
    }

    @Override
    public boolean completeProcessing(UUID intentId, UUID claimId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                outboxDao.completeProcessing(intentId, claimId, clock.instant()) == 1));
    }

    @Override
    public void releaseProcessingClaim(UUID intentId, UUID claimId) {
        transactionTemplate.executeWithoutResult(status ->
                outboxDao.releaseProcessingClaim(intentId, claimId, clock.instant()));
    }

    private DispatchExecution claim(UUID intentId) {
        UUID claimId = UUID.randomUUID();
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (outboxDao.claimDispatch(intentId, claimId, now, properties.getMaxAttempts()) != 1) {
                return null;
            }
            UserProfileSyncOutbox outbox = outboxDao.findById(intentId).orElseThrow();
            return new DispatchExecution(outbox.getPayload(), outbox.getAttempts(), claimId, outbox);
        });
    }

    private boolean complete(UUID intentId, UUID claimId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                outboxDao.completeDispatch(intentId, claimId, clock.instant()) == 1));
    }

    private void fail(UUID intentId, DispatchExecution execution) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Instant retryAt = now.plus(backoff.delayFor(execution.attempts()));
            int failed = outboxDao.failClaimedDispatch(intentId, execution.claimId(),
                    properties.getMaxAttempts(), retryAt, now, BROKER_RETRY, BROKER_EXHAUSTED);
            if (failed == 0) {
                outboxDao.completeDispatch(intentId, execution.claimId(), now);
            }
        });
    }

    private void terminalize(UUID intentId, UUID claimId, String reason) {
        transactionTemplate.executeWithoutResult(status ->
                outboxDao.terminalizeClaimedDispatch(intentId, claimId, clock.instant(), reason));
    }

    private void refreshMetrics(Instant now) {
        Instant oldest = outboxDao.findOldestDispatchCreatedAt();
        metrics.refresh(outboxDao.countDispatchBacklog(),
                oldest == null ? Duration.ZERO : Duration.between(oldest, now),
                outboxDao.countTerminalBrokerPublishFailures());
    }

    private String writePayload(UserUpdatedEventDto event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Iyzico profile sync payload could not be serialized", exception);
        }
    }

    private record DispatchExecution(String payload, int attempts, UUID claimId,
                                     UserProfileSyncOutbox outbox) {
    }
}

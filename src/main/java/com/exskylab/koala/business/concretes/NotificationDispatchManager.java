package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import com.exskylab.koala.core.helpers.NotificationDispatchBackoff;
import com.exskylab.koala.core.logging.NotificationDispatchLogContext;
import com.exskylab.koala.core.monitoring.NotificationDispatchMetrics;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class NotificationDispatchManager implements NotificationDispatchService {

    private static final String BROKER_RETRY = "BROKER_PUBLISH_RETRY_SCHEDULED";
    private static final String BROKER_EXHAUSTED = "BROKER_PUBLISH_EXHAUSTED";
    private static final String INVALID_PAYLOAD = "DISPATCH_PAYLOAD_INVALID";
    private static final String LEGACY_PAYLOAD_UNAVAILABLE = "LEGACY_DISPATCH_PAYLOAD_UNAVAILABLE";

    private final NotificationDao notificationDao;
    private final NotificationProducerService producer;
    private final NotificationDispatchProperties properties;
    private final NotificationDispatchBackoff backoff;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationDispatchMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public NotificationDispatchManager(NotificationDao notificationDao, NotificationProducerService producer,
                                       NotificationDispatchProperties properties,
                                       NotificationDispatchBackoff backoff, ObjectMapper objectMapper, Clock clock,
                                       NotificationDispatchMetrics metrics,
                                       PlatformTransactionManager transactionManager) {
        this.notificationDao = notificationDao;
        this.producer = producer;
        this.properties = properties;
        this.backoff = backoff;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<UUID> findDueTaskIds() {
        Instant now = clock.instant();
        if (!properties.isEnabled()) {
            refreshMetrics(now);
            return List.of();
        }
        List<UUID> result = transactionTemplate.execute(status -> {
            notificationDao.terminalizeLegacyQueued(now.minus(properties.getLegacyGracePeriod()), now,
                    LEGACY_PAYLOAD_UNAVAILABLE);
            notificationDao.terminalizeExhaustedDispatches(properties.getMaxAttempts(), now, BROKER_EXHAUSTED);
            notificationDao.releaseStaleDispatchClaims(now.minus(properties.getClaimTimeout()), now);
            return notificationDao.findDueDispatchIds(now, properties.getMaxAttempts(),
                    PageRequest.of(0, properties.getBatchSize()));
        });
        refreshMetrics(now);
        return result == null ? List.of() : result;
    }

    @Override
    public boolean runTask(UUID notificationId) {
        if (!properties.isEnabled()) {
            return false;
        }
        DispatchExecution execution = claim(notificationId);
        if (execution == null) {
            return false;
        }
        try (var ignored = NotificationDispatchLogContext.activate(execution.notification())) {
            try {
                publish(execution);
            } catch (JacksonException exception) {
                terminalize(notificationId, execution.claimId(), INVALID_PAYLOAD);
                return false;
            } catch (RuntimeException exception) {
                log.warn("Notification {} could not be published on attempt {}", notificationId, execution.attempts());
                fail(notificationId, execution);
                return false;
            }
            return complete(notificationId, execution.claimId());
        }
    }

    private DispatchExecution claim(UUID notificationId) {
        UUID claimId = UUID.randomUUID();
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (notificationDao.claimDispatch(notificationId, claimId, now, properties.getMaxAttempts()) != 1) {
                return null;
            }
            Notification notification = notificationDao.findById(notificationId).orElseThrow();
            return new DispatchExecution(notification.getChannel(), notification.getDispatchPayload(),
                    notification.getDispatchAttempts(), claimId, notification);
        });
    }

    private void publish(DispatchExecution execution) throws JacksonException {
        switch (execution.channel()) {
            case EMAIL -> producer.sendEmailTask(objectMapper.readValue(execution.payload(), EmailTaskDto.class));
            case SMS -> producer.sendSmsTask(objectMapper.readValue(execution.payload(), SmsTaskDto.class));
            case PUSH -> producer.sendPushTask(objectMapper.readValue(execution.payload(), PushTaskDto.class));
            case IN_APP -> throw new IllegalStateException("IN_APP notifications do not use Rabbit dispatch");
        }
    }

    private boolean complete(UUID notificationId, UUID claimId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                notificationDao.completeDispatch(notificationId, claimId, clock.instant()) == 1));
    }

    private void fail(UUID notificationId, DispatchExecution execution) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Instant retryAt = now.plus(backoff.delayFor(execution.attempts()));
            int failed = notificationDao.failClaimedDispatch(notificationId, execution.claimId(),
                    properties.getMaxAttempts(), retryAt, now, BROKER_RETRY, BROKER_EXHAUSTED);
            if (failed == 0) {
                notificationDao.completeDispatch(notificationId, execution.claimId(), now);
            }
        });
    }

    private void terminalize(UUID notificationId, UUID claimId, String reason) {
        transactionTemplate.executeWithoutResult(status -> notificationDao.terminalizeClaimedDispatch(
                notificationId, claimId, clock.instant(), reason));
    }

    private void refreshMetrics(Instant now) {
        Instant oldest = notificationDao.findOldestDispatchCreatedAt();
        metrics.refresh(notificationDao.countDispatchBacklog(),
                oldest == null ? Duration.ZERO : Duration.between(oldest, now),
                notificationDao.countTerminalBrokerPublishFailures());
    }

    private record DispatchExecution(NotificationChannel channel, String payload, int attempts, UUID claimId,
                                     Notification notification) {
    }
}

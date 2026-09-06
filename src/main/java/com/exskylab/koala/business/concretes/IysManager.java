package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.constants.IysMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.utilities.iys.IysConsentRequest;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.core.utilities.iys.IysReconciliationTarget;
import com.exskylab.koala.core.utilities.iys.IysRecipientNormalizer;
import com.exskylab.koala.core.utilities.iys.IysFailureClassifier;
import com.exskylab.koala.core.utilities.iys.IysRecipientCipher;
import com.exskylab.koala.dataAccess.IysConsentTaskDao;
import com.exskylab.koala.dataAccess.IysPermissionStateDao;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.IysFailureCode;
import com.exskylab.koala.entities.IysPermissionState;
import com.exskylab.koala.entities.IysPermissionStatus;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IysManager implements IysService {

    private final IysConsentTaskDao iysConsentTaskDao;
    private final IysProviderClient providerClient;
    private final IysPermissionStateDao iysPermissionStateDao;
    private final IysProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final IysRecipientCipher recipientCipher;

    public IysManager(IysConsentTaskDao iysConsentTaskDao, IysPermissionStateDao iysPermissionStateDao,
                      IysProviderClient providerClient, IysProperties properties, Clock clock,
                      PlatformTransactionManager transactionManager, IysRecipientCipher recipientCipher) {
        this.iysConsentTaskDao = iysConsentTaskDao;
        this.iysPermissionStateDao = iysPermissionStateDao;
        this.providerClient = providerClient;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.recipientCipher = recipientCipher;
    }

    @Override
    @Transactional
    public void enqueue(User user, CommercialMessageConsent consent, NotificationChannel channel, boolean granted,
                        ClientContext client) {
        String recipient = normalizedRecipientOf(user, channel);
        if (channel == NotificationChannel.PUSH || channel == NotificationChannel.IN_APP) {
            return;
        }
        if (recipient == null || recipient.isBlank()) {
            if (granted) {
                throw new ValidationException(IysMessages.VERIFIED_RECIPIENT_REQUIRED);
            }
            return;
        }

        lockDecisionStream(user.getId(), channel);

        IysConsentTask task = new IysConsentTask();
        task.setUser(user);
        task.setConsent(consent);
        task.setChannel(channel);
        task.setRecipient(recipient);
        task.setGranted(granted);
        task.setProviderSource(properties.sourceFor(consent.getSource(), client == null ? null : client.platform()));
        task.setStatus(IysConsentTaskStatus.PENDING);
        task.setNextAttemptAt(clock.instant());
        IysConsentTask saved = iysConsentTaskDao.saveAndFlush(task);
        iysConsentTaskDao.supersedeOlderPending(saved.getId(), clock.instant());
    }

    @Override
    public boolean runTask(UUID taskId) {
        if (!properties.isConfigured()) {
            return false;
        }
        try {
            TaskExecution execution = claim(taskId);
            if (execution == null) {
                return false;
            }
            if (execution.registrationAcceptedAt() == null) {
                providerClient.registerConsent(new IysConsentRequest(execution.recipient(), execution.channel(),
                        execution.granted(), execution.recordedAt(), execution.providerSource()));
                markRegistrationAccepted(taskId);
            }
            var permission = providerClient.lookupConsent(execution.recipient(), execution.channel());
            if (!sameRecipient(execution.recipient(), permission.recipient(), execution.channel())) {
                throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
            }
            if (permission.granted() != execution.granted()) {
                throw new ExternalServiceException(IysMessages.REGISTRY_PENDING);
            }
            return complete(taskId, permission);
        } catch (RuntimeException exception) {
            fail(taskId, IysFailureClassifier.classify(exception));
            return false;
        }
    }

    @Override
    public List<UUID> findDueTaskIds() {
        Instant now = clock.instant();
        List<UUID> result = transactionTemplate.execute(status -> {
            iysConsentTaskDao.expireRetainedRecipients(now);
            if (!properties.isConfigured() || !properties.getRetry().isEnabled()) {
                return List.of();
            }
            iysConsentTaskDao.recoverStaleClaims(now.minus(properties.getClaimTimeout()), now);
            return iysConsentTaskDao.findDueIds(IysConsentTaskStatus.PENDING, now,
                    PageRequest.of(0, properties.getRetry().getBatchSize()));
        });
        return result == null ? List.of() : result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAllowed(User user, NotificationChannel channel, String destination) {
        if (user == null || !properties.isConfigured()) {
            return false;
        }
        String recipient = normalizedRecipientOf(user, channel);
        if (!sameRecipient(recipient, destination, channel)) {
            return false;
        }
        return iysPermissionStateDao.findByUserIdAndChannel(user.getId(), channel)
                .filter(state -> state.getStatus() == IysPermissionStatus.GRANTED)
                .filter(state -> sameRecipient(recipient, state.getRecipient(), channel))
                .filter(state -> !state.getVerifiedAt().isBefore(
                        clock.instant().minus(properties.getPermissionMaxAge())))
                .isPresent();
    }

    @Override
    public IysPermissionResult refreshPermission(User user, NotificationChannel channel) {
        if (!properties.isConfigured()) {
            throw new ExternalServiceException(IysMessages.PROVIDER_NOT_CONFIGURED);
        }
        if (channel == NotificationChannel.PUSH || channel == NotificationChannel.IN_APP) {
            throw new ValidationException(IysMessages.CHANNEL_UNSUPPORTED);
        }
        String recipient = normalizedRecipientOf(user, channel);
        if (recipient == null || recipient.isBlank()) {
            throw new ValidationException(IysMessages.VERIFIED_RECIPIENT_REQUIRED);
        }
        var permission = providerClient.lookupConsent(recipient, channel);
        if (!sameRecipient(recipient, permission.recipient(), channel)) {
            throw new ExternalServiceException(IysMessages.INVALID_RESPONSE);
        }
        return permission;
    }

    @Override
    @Transactional
    public boolean applyReconciledPermission(User user, IysPermissionResult permission) {
        lockDecisionStream(user.getId(), permission.channel());
        if (!sameRecipient(normalizedRecipientOf(user, permission.channel()), permission.recipient(),
                permission.channel())) {
            return false;
        }
        IysConsentTask latest = iysConsentTaskDao
                .findTopByUserIdAndChannelOrderByDecisionSequenceDesc(user.getId(), permission.channel())
                .orElse(null);
        if (latest != null && latest.getStatus() != IysConsentTaskStatus.COMPLETED
                && latest.getStatus() != IysConsentTaskStatus.SUPERSEDED
                && latest.isGranted() != permission.granted()
                && !permission.recordedAt().isAfter(latest.getConsent().getRecordedAt())) {
            return false;
        }
        IysPermissionState current = iysPermissionStateDao.findByUserIdAndChannel(user.getId(), permission.channel())
                .orElse(null);
        if (current != null && current.getProviderRecordedAt().isAfter(permission.recordedAt())) {
            return false;
        }
        storePermission(user, permission);
        return !permission.granted();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IysReconciliationTarget> findStalePermissions() {
        if (!properties.isConfigured() || !properties.getReconciliation().isEnabled()) {
            return List.of();
        }
        return iysPermissionStateDao.findByNextReconciliationAtLessThanEqualOrderByNextReconciliationAtAsc(clock.instant(),
                        PageRequest.of(0, properties.getReconciliation().getBatchSize()))
                .stream()
                .map(state -> new IysReconciliationTarget(state.getUser().getId(), state.getChannel()))
                .toList();
    }

    @Override
    public void recordReconciliationFailure(UUID userId, NotificationChannel channel, IysFailureCode failureCode) {
        transactionTemplate.executeWithoutResult(status -> {
            lockDecisionStream(userId, channel);
            iysPermissionStateDao.findByUserIdAndChannel(userId, channel).ifPresent(state -> {
                    int attempts = state.getReconciliationAttempts() + 1;
                    state.setReconciliationAttempts(attempts);
                    state.setLastFailureCode(failureCode);
                    state.setNextReconciliationAt(clock.instant().plus(backoffFor(attempts)));
                iysPermissionStateDao.save(state);
            });
        });
    }

    private void storePermission(User user, IysPermissionResult permission) {
        IysPermissionState state = iysPermissionStateDao.findByUserIdAndChannel(user.getId(), permission.channel())
                .orElseGet(IysPermissionState::new);
        state.setUser(user);
        state.setChannel(permission.channel());
        state.setRecipient(permission.recipient());
        state.setStatus(permission.granted() ? IysPermissionStatus.GRANTED : IysPermissionStatus.REJECTED);
        state.setProviderRecordedAt(permission.recordedAt());
        state.setVerifiedAt(clock.instant());
        state.setProviderSource(permission.source());
        state.setNextReconciliationAt(clock.instant().plus(properties.getPermissionMaxAge()));
        state.setReconciliationAttempts(0);
        state.setLastFailureCode(null);
        iysPermissionStateDao.save(state);
    }

    private TaskExecution claim(UUID taskId) {
        try {
            return transactionTemplate.execute(status -> {
                IysConsentTask task = iysConsentTaskDao.findWithDetailsById(taskId).orElse(null);
                if (task == null || task.getStatus() != IysConsentTaskStatus.PENDING) {
                    return null;
                }
                if (iysConsentTaskDao.existsNewerDecision(taskId)) {
                    task.setStatus(IysConsentTaskStatus.SUPERSEDED);
                    task.setCompletedAt(clock.instant());
                    iysConsentTaskDao.save(task);
                    return null;
                }
                if (iysConsentTaskDao.claim(taskId, clock.instant()) != 1) {
                    return null;
                }
                IysConsentTask claimed = iysConsentTaskDao.findWithDetailsById(taskId).orElseThrow();
                String recipient = claimed.getRetainedRecipientCiphertext() == null
                        ? claimed.getRecipient()
                        : recipientCipher.decrypt(claimed.getRetainedRecipientCiphertext());
                return new TaskExecution(recipient, claimed.getChannel(), claimed.isGranted(),
                        claimed.getConsent().getRecordedAt(), claimed.getProviderSource(),
                        claimed.getRegistrationAcceptedAt());
            });
        } catch (DataIntegrityViolationException exception) {
            return null;
        }
    }

    private void markRegistrationAccepted(UUID taskId) {
        transactionTemplate.executeWithoutResult(status -> iysConsentTaskDao.findById(taskId)
                .filter(task -> task.getStatus() == IysConsentTaskStatus.PROCESSING)
                .ifPresent(task -> {
                    task.setRegistrationAcceptedAt(clock.instant());
                    iysConsentTaskDao.save(task);
                }));
    }

    private boolean complete(UUID taskId, IysPermissionResult permission) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            IysConsentTask task = iysConsentTaskDao.findWithDetailsById(taskId).orElse(null);
            if (task == null || task.getStatus() != IysConsentTaskStatus.PROCESSING) {
                return false;
            }
            lockDecisionStream(task.getUserId(), task.getChannel());
            if (iysConsentTaskDao.existsNewerDecision(taskId)) {
                task.setStatus(IysConsentTaskStatus.SUPERSEDED);
                task.setCompletedAt(clock.instant());
                task.setProcessingStartedAt(null);
                iysConsentTaskDao.save(task);
                return false;
            }
            if (!executionIsRetained(task)) {
                storePermission(task.getUser(), permission);
            }
            task.setStatus(IysConsentTaskStatus.COMPLETED);
            task.setCompletedAt(clock.instant());
            task.setProcessingStartedAt(null);
            task.setLastFailureCode(null);
            task.setRetainedRecipientCiphertext(null);
            task.setRecipientRetainUntil(null);
            iysConsentTaskDao.save(task);
            return true;
        }));
    }

    private void fail(UUID taskId, IysFailureCode failureCode) {
        transactionTemplate.executeWithoutResult(status -> {
            IysConsentTask task = iysConsentTaskDao.findById(taskId).orElse(null);
            if (task == null || task.getStatus() != IysConsentTaskStatus.PROCESSING) {
                return;
            }
            task.setProcessingStartedAt(null);
            if (iysConsentTaskDao.existsNewerDecision(taskId)) {
                task.setStatus(IysConsentTaskStatus.SUPERSEDED);
                task.setCompletedAt(clock.instant());
            } else {
                task.setLastFailureCode(failureCode);
                boolean retained = task.getRetainedRecipientCiphertext() != null;
                if (retained && !clock.instant().isBefore(task.getRecipientRetainUntil())) {
                    task.setStatus(IysConsentTaskStatus.EXHAUSTED);
                    task.setRetainedRecipientCiphertext(null);
                    task.setRecipientRetainUntil(null);
                } else if (!retained && task.getAttempts() >= properties.getRetry().getMaxAttempts()) {
                    task.setStatus(IysConsentTaskStatus.EXHAUSTED);
                } else {
                    task.setStatus(IysConsentTaskStatus.PENDING);
                    task.setNextAttemptAt(clock.instant().plus(backoffFor(task.getAttempts())));
                }
            }
            iysConsentTaskDao.save(task);
        });
    }

    private Duration backoffFor(int attempts) {
        Duration backoff = properties.getRetry().getInitialBackoff()
                .multipliedBy(1L << Math.min(attempts - 1, 20));
        return backoff.compareTo(properties.getRetry().getMaxBackoff()) > 0
                ? properties.getRetry().getMaxBackoff()
                : backoff;
    }

    private void lockDecisionStream(UUID userId, NotificationChannel channel) {
        iysConsentTaskDao.lockDecisionStream(userId, channel.name());
    }

    private boolean executionIsRetained(IysConsentTask task) {
        return task.getRetainedRecipientCiphertext() != null;
    }

    private String recipientOf(User user, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> user.getEmail();
            case SMS -> user.getPhoneNumber();
            case PUSH, IN_APP -> null;
        };
    }

    private String normalizedRecipientOf(User user, NotificationChannel channel) {
        return IysRecipientNormalizer.normalize(recipientOf(user, channel), channel);
    }

    private boolean sameRecipient(String left, String right, NotificationChannel channel) {
        String normalizedLeft = IysRecipientNormalizer.normalize(left, channel);
        String normalizedRight = IysRecipientNormalizer.normalize(right, channel);
        return normalizedLeft != null && normalizedRight != null
                && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private record TaskExecution(String recipient, NotificationChannel channel, boolean granted,
                                 Instant recordedAt, String providerSource, Instant registrationAcceptedAt) {
    }
}

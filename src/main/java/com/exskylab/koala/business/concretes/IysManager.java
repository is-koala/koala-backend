package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.properties.IysProperties;
import com.exskylab.koala.core.utilities.iys.IysConsentRequest;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.core.utilities.iys.IysReconciliationTarget;
import com.exskylab.koala.dataAccess.IysConsentTaskDao;
import com.exskylab.koala.dataAccess.IysPermissionStateDao;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.IysConsentTask;
import com.exskylab.koala.entities.IysConsentTaskStatus;
import com.exskylab.koala.entities.IysPermissionState;
import com.exskylab.koala.entities.IysPermissionStatus;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public IysManager(IysConsentTaskDao iysConsentTaskDao, IysPermissionStateDao iysPermissionStateDao,
                      IysProviderClient providerClient, IysProperties properties, Clock clock) {
        this.iysConsentTaskDao = iysConsentTaskDao;
        this.iysPermissionStateDao = iysPermissionStateDao;
        this.providerClient = providerClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void enqueue(User user, CommercialMessageConsent consent, NotificationChannel channel, boolean granted) {
        String recipient = recipientOf(user, channel);
        if (recipient == null) {
            return;
        }

        IysConsentTask task = new IysConsentTask();
        task.setUser(user);
        task.setConsent(consent);
        task.setChannel(channel);
        task.setRecipient(recipient);
        task.setGranted(granted);
        task.setStatus(IysConsentTaskStatus.PENDING);
        task.setNextAttemptAt(clock.instant());
        iysConsentTaskDao.save(task);
    }

    @Override
    @Transactional
    public boolean runTask(UUID taskId) {
        IysConsentTask task = iysConsentTaskDao.findWithDetailsById(taskId).orElse(null);
        if (task == null || task.getStatus() != IysConsentTaskStatus.PENDING) {
            return false;
        }
        if (iysConsentTaskDao.existsNewerDecision(task.getUser().getId(), task.getChannel(),
                task.getConsent().getRecordedAt())) {
            task.setStatus(IysConsentTaskStatus.SUPERSEDED);
            task.setCompletedAt(clock.instant());
            iysConsentTaskDao.save(task);
            return false;
        }
        if (!properties.isConfigured()) {
            return false;
        }

        task.setAttempts(task.getAttempts() + 1);
        try {
            providerClient.registerConsent(new IysConsentRequest(task.getRecipient(), task.getChannel(),
                    task.isGranted(), task.getConsent().getRecordedAt(), task.getConsent().getSource()));
            var permission = providerClient.lookupConsent(task.getRecipient(), task.getChannel());
            storePermission(task.getUser(), permission);
            if (permission.granted() != task.isGranted()) {
                throw new IllegalStateException("IYS consent is not reflected by the registry yet");
            }
            task.setStatus(IysConsentTaskStatus.COMPLETED);
            task.setCompletedAt(clock.instant());
            task.setLastError(null);
            iysConsentTaskDao.save(task);
            return true;
        } catch (Exception exception) {
            task.setLastError(truncate(exception.getMessage()));
            if (task.getAttempts() >= properties.getRetry().getMaxAttempts()) {
                task.setStatus(IysConsentTaskStatus.EXHAUSTED);
            } else {
                task.setNextAttemptAt(clock.instant().plus(backoffFor(task.getAttempts())));
            }
            iysConsentTaskDao.save(task);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueTaskIds() {
        if (!properties.isConfigured() || !properties.getRetry().isEnabled()) {
            return List.of();
        }
        return iysConsentTaskDao.findDueIds(IysConsentTaskStatus.PENDING, clock.instant(),
                PageRequest.of(0, properties.getRetry().getBatchSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAllowed(User user, NotificationChannel channel) {
        if (user == null || !properties.isConfigured()) {
            return false;
        }
        String recipient = recipientOf(user, channel);
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        return iysPermissionStateDao.findByUserIdAndChannel(user.getId(), channel)
                .filter(state -> state.getStatus() == IysPermissionStatus.GRANTED)
                .filter(state -> recipient.equalsIgnoreCase(state.getRecipient()))
                .filter(state -> !state.getVerifiedAt().isBefore(
                        clock.instant().minus(properties.getPermissionMaxAge())))
                .isPresent();
    }

    @Override
    @Transactional
    public IysPermissionResult refreshPermission(User user, NotificationChannel channel) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("IYS provider is not configured");
        }
        String recipient = recipientOf(user, channel);
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("User has no recipient for the IYS channel");
        }
        var permission = providerClient.lookupConsent(recipient, channel);
        storePermission(user, permission);
        return permission;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IysReconciliationTarget> findStalePermissions() {
        if (!properties.isConfigured() || !properties.getReconciliation().isEnabled()) {
            return List.of();
        }
        Instant staleBefore = clock.instant().minus(properties.getPermissionMaxAge());
        return iysPermissionStateDao.findByVerifiedAtLessThanEqualOrderByVerifiedAtAsc(staleBefore,
                        PageRequest.of(0, properties.getReconciliation().getBatchSize()))
                .stream()
                .map(state -> new IysReconciliationTarget(state.getUser().getId(), state.getChannel()))
                .toList();
    }

    private void storePermission(User user, IysPermissionResult permission) {
        IysPermissionState state = iysPermissionStateDao.findByUserIdAndChannel(user.getId(), permission.channel())
                .orElseGet(IysPermissionState::new);
        state.setUser(user);
        state.setChannel(permission.channel());
        state.setRecipient(recipientOf(user, permission.channel()));
        state.setStatus(permission.granted() ? IysPermissionStatus.GRANTED : IysPermissionStatus.REJECTED);
        state.setProviderRecordedAt(permission.recordedAt());
        state.setVerifiedAt(clock.instant());
        state.setProviderSource(permission.source());
        iysPermissionStateDao.save(state);
    }

    private Duration backoffFor(int attempts) {
        Duration backoff = properties.getRetry().getInitialBackoff()
                .multipliedBy(1L << Math.min(attempts - 1, 20));
        return backoff.compareTo(properties.getRetry().getMaxBackoff()) > 0
                ? properties.getRetry().getMaxBackoff()
                : backoff;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String recipientOf(User user, NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> user.getEmail();
            case SMS -> user.getPhoneNumber();
            case PUSH, IN_APP -> null;
        };
    }
}

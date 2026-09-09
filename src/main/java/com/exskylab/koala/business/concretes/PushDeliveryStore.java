package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.PushFailureCode;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PushDeliveryStore {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final NotificationDao notificationDao;
    private final DeviceService deviceService;
    private final PushDeliveryProperties properties;
    private final ObjectMapper objectMapper;

    public PushDeliveryStore(NotificationDao notificationDao, DeviceService deviceService,
                             PushDeliveryProperties properties, ObjectMapper objectMapper) {
        this.notificationDao = notificationDao;
        this.deviceService = deviceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<PushDeliveryClaim> claim(UUID notificationId, Instant now) {
        Optional<Notification> found = notificationDao.findForPushDelivery(notificationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Notification notification = found.get();
        if (notification.getChannel() != NotificationChannel.PUSH
                || notification.getStatus() != NotificationStatus.QUEUED
                || notification.getSentAt() != null
                || notification.getPushNextAttemptAt() != null
                && notification.getPushNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }
        if (notification.getPushAttemptCount() >= properties.getMaxAttempts()) {
            markTerminal(notification, PushFailureCode.RETRIES_EXHAUSTED, now);
            return Optional.empty();
        }
        if (notification.getRecipient() == null || notification.getDestinationDevice() == null) {
            markTerminal(notification, PushFailureCode.INVALID_MESSAGE, now);
            return Optional.empty();
        }

        Optional<String> currentToken = deviceService.currentPushToken(
                notification.getRecipient().getId(), notification.getDestinationDevice().getId());
        if (currentToken.isEmpty()) {
            notification.setStatus(NotificationStatus.SUPPRESSED);
            notification.setFailureReason(PushFailureCode.TOKEN_NO_LONGER_ASSIGNED.name());
            notification.setProcessedAt(now);
            notification.setPushNextAttemptAt(null);
            notificationDao.saveAndFlush(notification);
            return Optional.empty();
        }

        Map<String, String> data;
        try {
            Map<String, String> parsed = notification.getPushDataPayload() == null
                    ? Map.of() : objectMapper.readValue(notification.getPushDataPayload(), STRING_MAP);
            if (parsed == null || parsed.entrySet().stream()
                    .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
                markTerminal(notification, PushFailureCode.INVALID_MESSAGE, now);
                return Optional.empty();
            }
            data = new HashMap<>(parsed);
        } catch (JacksonException exception) {
            markTerminal(notification, PushFailureCode.INVALID_MESSAGE, now);
            return Optional.empty();
        }
        data.put("notificationId", notification.getId().toString());

        UUID claimId = UUID.randomUUID();
        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setProcessedAt(now);
        notification.setPushProcessingStartedAt(now);
        notification.setPushProcessingClaimId(claimId);
        notification.setPushAttemptCount(notification.getPushAttemptCount() + 1);
        notificationDao.saveAndFlush(notification);

        return Optional.of(new PushDeliveryClaim(notification.getId(), claimId,
                notification.getRecipient().getId(), notification.getDestinationDevice().getId(), currentToken.get(),
                notification.getSubject(), notification.getBody(), Map.copyOf(data),
                notification.getPushAttemptCount()));
    }

    @Transactional
    public void complete(PushDeliveryClaim claim, String externalId, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> {
                    notification.setExternalId(externalId);
                    notification.setStatus(NotificationStatus.SENT);
                    notification.setSentAt(now);
                    notification.setFailureReason(null);
                    notification.setPushNextAttemptAt(null);
                    clearClaim(notification);
                    notificationDao.saveAndFlush(notification);
                });
    }

    @Transactional
    public void fail(PushDeliveryClaim claim, PushFailureCode code, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> markTerminal(notification, code, now));
    }

    @Transactional
    public void suppress(PushDeliveryClaim claim, PushFailureCode code, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> {
                    notification.setStatus(NotificationStatus.SUPPRESSED);
                    notification.setFailureReason(code.name());
                    notification.setProcessedAt(now);
                    notification.setPushNextAttemptAt(null);
                    clearClaim(notification);
                    notificationDao.saveAndFlush(notification);
                });
    }

    @Transactional
    public void retry(PushDeliveryClaim claim, PushFailureCode code, Instant nextAttemptAt, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> {
                    if (notification.getPushAttemptCount() >= properties.getMaxAttempts()) {
                        markTerminal(notification, PushFailureCode.RETRIES_EXHAUSTED, now);
                        return;
                    }
                    notification.setStatus(NotificationStatus.QUEUED);
                    notification.setFailureReason(code.name());
                    notification.setPushNextAttemptAt(nextAttemptAt);
                    clearClaim(notification);
                    notificationDao.saveAndFlush(notification);
                });
    }

    @Transactional
    public void deferForTokenContention(PushDeliveryClaim claim, Instant nextAttemptAt, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> {
                    notification.setStatus(NotificationStatus.QUEUED);
                    notification.setFailureReason(PushFailureCode.TOKEN_SERIALIZATION_BUSY.name());
                    notification.setPushNextAttemptAt(nextAttemptAt);
                    notification.setProcessedAt(now);
                    notification.setPushAttemptCount(Math.max(0, notification.getPushAttemptCount() - 1));
                    clearClaim(notification);
                    notificationDao.saveAndFlush(notification);
                });
    }

    @Transactional
    public void recordInfrastructureFailure(PushDeliveryClaim claim, Instant now) {
        notificationDao.findForPushDelivery(claim.notificationId())
                .filter(notification -> ownsClaim(notification, claim.claimId()))
                .ifPresent(notification -> {
                    if (notification.getPushAttemptCount() >= properties.getMaxAttempts()) {
                        markTerminal(notification, PushFailureCode.RETRIES_EXHAUSTED, now);
                        return;
                    }
                    notification.setStatus(NotificationStatus.QUEUED);
                    notification.setFailureReason(PushFailureCode.PROVIDER_INTERNAL.name());
                    notification.setPushNextAttemptAt(now);
                    clearClaim(notification);
                    notificationDao.saveAndFlush(notification);
                });
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueIds(Instant now) {
        return notificationDao.findDuePushDeliveryIds(now, PageRequest.of(0, properties.getBatchSize()));
    }

    @Transactional
    public int recoverStale(Instant staleBefore, Instant now) {
        List<UUID> ids = notificationDao.findStalePushDeliveryIds(
                staleBefore, PageRequest.of(0, properties.getBatchSize()));
        int recovered = 0;
        for (UUID id : ids) {
            Optional<Notification> found = notificationDao.findForPushDelivery(id);
            if (found.isEmpty()) {
                continue;
            }
            Notification notification = found.get();
            if (notification.getStatus() != NotificationStatus.PROCESSING
                    || notification.getPushProcessingStartedAt() == null
                    || !notification.getPushProcessingStartedAt().isBefore(staleBefore)) {
                continue;
            }
            if (notification.getPushAttemptCount() >= properties.getMaxAttempts()) {
                markTerminal(notification, PushFailureCode.RETRIES_EXHAUSTED, now);
            } else {
                notification.setStatus(NotificationStatus.QUEUED);
                notification.setFailureReason(PushFailureCode.STALE_CLAIM_RECOVERED.name());
                notification.setPushNextAttemptAt(now);
                clearClaim(notification);
                notificationDao.saveAndFlush(notification);
            }
            recovered++;
        }
        return recovered;
    }

    private boolean ownsClaim(Notification notification, UUID claimId) {
        return notification.getStatus() == NotificationStatus.PROCESSING
                && claimId.equals(notification.getPushProcessingClaimId())
                && notification.getSentAt() == null;
    }

    private void markTerminal(Notification notification, PushFailureCode code, Instant now) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setFailureReason(code.name());
        notification.setProcessedAt(now);
        notification.setPushNextAttemptAt(null);
        clearClaim(notification);
        notificationDao.saveAndFlush(notification);
    }

    private void clearClaim(Notification notification) {
        notification.setPushProcessingClaimId(null);
        notification.setPushProcessingStartedAt(null);
    }
}

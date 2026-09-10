package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.NotificationMessages;
import com.exskylab.koala.core.consumers.NotificationDeliveryBusyException;
import com.exskylab.koala.core.dtos.notification.request.*;
import com.exskylab.koala.core.exceptions.*;
import com.exskylab.koala.core.logging.NotificationDispatchLogContext;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.core.privacy.PushTokenMutationLock;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationManager implements NotificationService {

    private static final Set<String> NOTIFICATION_SORTABLE = Set.of("createdAt");

    private static final Sort NOTIFICATION_SORT = Sort.by(Sort.Direction.DESC, "createdAt");


    private final PageableSanitizer pageableSanitizer;
    private final NotificationDao notificationDao;
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final SecurityService securityService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final IysService iysService;
    private final PushTokenMutationLock pushTokenMutationLock;
    private final NotificationDispatchProperties dispatchProperties;

    public NotificationManager(NotificationDao notificationDao,
                               DeviceService deviceService,
                                 ObjectMapper objectMapper,
                               SecurityService securityService,
                                PageableSanitizer pageableSanitizer,
                               NotificationPreferenceService notificationPreferenceService,
                               UnsubscribeTokenService unsubscribeTokenService,
                               IysService iysService,
                               PushTokenMutationLock pushTokenMutationLock,
                               NotificationDispatchProperties dispatchProperties) {
        this.notificationDao = notificationDao;
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.securityService = securityService;
            this.pageableSanitizer = pageableSanitizer;
        this.notificationPreferenceService = notificationPreferenceService;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.iysService = iysService;
        this.pushTokenMutationLock = pushTokenMutationLock;
        this.dispatchProperties = dispatchProperties;
    }

    private boolean suppressed(User recipient, NotificationCategory category, NotificationChannel channel,
                               String destination) {
        if (recipient != null && !notificationPreferenceService.isAllowed(recipient.getId(), category, channel)) {
            log.info("{} notification in category {} suppressed for user {} by preference",
                    channel, category, recipient.getId());
            return true;
        }
        if (category != null && category.getKind().isCommercial()
                && (channel == NotificationChannel.EMAIL || channel == NotificationChannel.SMS)
                && !iysService.isAllowed(recipient, channel, destination)) {
            log.info("{} notification in category {} suppressed by IYS permission for user {}",
                    channel, category, recipient == null ? "unknown" : recipient.getId());
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Notification sendEmail(SendEmailDto sendEmailDto, DispatchPriority dispatchPriority, boolean isSystemMail) {

        String recipientInfo;
        if (sendEmailDto.getRecipient() != null) {
            recipientInfo = "userId: " + sendEmailDto.getRecipient().getId();
        } else {
            recipientInfo = "external address";
        }

        log.info("Sending email notification to {} destinationEmail: {}", recipientInfo, LogMasks.email(sendEmailDto.getDestinationEmail()));

        if (suppressed(sendEmailDto.getRecipient(), sendEmailDto.getCategory(), NotificationChannel.EMAIL,
                sendEmailDto.getDestinationEmail())) {
            return null;
        }

        User sender;
        if (isSystemMail) {
            sender = securityService.getSystemUser();
        } else {
            sender = securityService.getAuthenticatedUser();
        }



        String finalTemplateName;
        Map<String, Object> finalTemplateParameters;
        String finalSubject;

        if (sendEmailDto.getTemplateName() != null) {
            finalTemplateName = sendEmailDto.getTemplateName();
            finalTemplateParameters = withUnsubscribeLink(sendEmailDto);
            finalSubject = sendEmailDto.getSubject();
        } else if (sendEmailDto.getHtmlBody() != null && sendEmailDto.getSubject() != null) {
            finalTemplateName = "raw-html-template";
            finalTemplateParameters = Map.of("htmlContent", sendEmailDto.getHtmlBody());
            finalSubject = sendEmailDto.getSubject();
        } else {
            log.error("Email notification could not be queued for {}. Cause: {}",
                    recipientInfo, NotificationMessages.TEMPLATE_OR_SUBJECT_NOT_PROVIDED);
            throw new ExternalServiceException(NotificationMessages.TEMPLATE_OR_SUBJECT_NOT_PROVIDED);
        }

        Notification notification = new Notification();
        notification.setRecipient(sendEmailDto.getRecipient());
        notification.setSender(sender);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(sendEmailDto.getCategory());
        notification.setDestinationEmail(sendEmailDto.getDestinationEmail());
        notification.setSubject(finalSubject);
        notification.setTemplateName(finalTemplateName);
        notification.setStatus(NotificationStatus.QUEUED);

        try {
            notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
        } catch (JacksonException e) {
            log.error("Error while serializing template parameters for email with {}", recipientInfo, e);
            throw new ExternalServiceException("{parameters.could.not.serialize}");
        }

        var savedNotification = notificationDao.save(notification);

        EmailTaskDto taskDto = new EmailTaskDto(
                savedNotification.getId(),
                savedNotification.getDestinationEmail(),
                finalSubject,
                finalTemplateName,
                finalTemplateParameters
        );

        prepareDispatch(savedNotification, taskDto, dispatchPriority);

        return savedNotification;
    }

    private Map<String, Object> withUnsubscribeLink(SendEmailDto sendEmailDto) {
        Map<String, Object> parameters = sendEmailDto.getTemplateParameters();

        if (sendEmailDto.getRecipient() == null || sendEmailDto.getCategory() == null
                || !sendEmailDto.getCategory().getKind().isCommercial()) {
            return parameters;
        }

        Map<String, Object> enriched = parameters == null
                ? new HashMap<>() : new HashMap<>(parameters);
        enriched.put("unsubscribeUrl", unsubscribeTokenService.issueLink(
                sendEmailDto.getRecipient(), sendEmailDto.getCategory(), NotificationChannel.EMAIL));
        return enriched;
    }

    @Override
    @Transactional
    public Notification sendSms(SendSmsDto sendSmsDto, DispatchPriority dispatchPriority, boolean isSystemSms) {
        log.info("Sending SMS notification to userId: {} destinationPhoneNumber: {}", sendSmsDto.getRecipient().getId(), LogMasks.phone(sendSmsDto.getDestinationPhoneNumber()));

        if (suppressed(sendSmsDto.getRecipient(), sendSmsDto.getCategory(), NotificationChannel.SMS,
                sendSmsDto.getDestinationPhoneNumber())) {
            return null;
        }

        User sender;
        if (isSystemSms) {
            sender = securityService.getSystemUser();
        } else {
            sender = securityService.getAuthenticatedUser();
        }


        String finalTemplateName;
        Map<String, Object> finalTemplateParameters;

        if (sendSmsDto.getTemplateName() != null) {
            finalTemplateName = sendSmsDto.getTemplateName();
            finalTemplateParameters = sendSmsDto.getTemplateParameters();
        } else if (sendSmsDto.getMessageBody() != null) {
            finalTemplateName = "raw-sms-template";
            finalTemplateParameters = Map.of("textContent", sendSmsDto.getMessageBody());
        } else {
            log.error("SMS notification could not be queued for userId: {}. Cause: {}",
                    sendSmsDto.getRecipient().getId(), NotificationMessages.TEMPLATE_OR_MESSAGE_BODY_NOT_PROVIDED);
            throw new ExternalServiceException(NotificationMessages.TEMPLATE_OR_MESSAGE_BODY_NOT_PROVIDED);
        }


        Notification notification = new Notification();
        notification.setRecipient(sendSmsDto.getRecipient());
        notification.setSender(sender);
        notification.setChannel(NotificationChannel.SMS);
        notification.setCategory(sendSmsDto.getCategory());
        notification.setDestinationPhoneNumber(sendSmsDto.getDestinationPhoneNumber());
        notification.setTemplateName(finalTemplateName);
        notification.setStatus(NotificationStatus.QUEUED);

        try {
            notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
        } catch (JacksonException e) {
            log.error("Error while serializing template parameters for email with userId: {}", sendSmsDto.getRecipient().getId(), e);
            throw new ExternalServiceException("{parameters.could.not.serialize}");
        }

        var savedNotification = notificationDao.save(notification);
        SmsTaskDto taskDto = new SmsTaskDto(
                savedNotification.getId(),
                savedNotification.getDestinationPhoneNumber(),
                finalTemplateName,
                finalTemplateParameters
        );

        prepareDispatch(savedNotification, taskDto, dispatchPriority);

        return savedNotification;

    }

    @Override
    @Transactional
    public void sendPushToUser(SendPushToUserDto sendPushToUserDto, DispatchPriority dispatchPriority, boolean isSystemPush) {
        if (sendPushToUserDto.getRecipient() == null) {
            throw new ValidationException(NotificationMessages.USER_ID_CANNOT_BE_NULL);
        }

        User recipient = sendPushToUserDto.getRecipient();
        preparePushRecipients(List.of(recipient.getId()));
        if (!securityService.isActiveUser(recipient.getId())) {
            log.info("Push notification suppressed because recipient {} is inactive", recipient.getId());
            return;
        }

        log.info("Dispatching push notification to userId: {}", recipient.getId());

        if (suppressed(recipient, sendPushToUserDto.getCategory(),
                NotificationChannel.PUSH, null)) {
            return;
        }

        User sender = isSystemPush ? securityService.getSystemUser(): securityService.getAuthenticatedUser();

        List<Device> usersDevices = deviceService.getDevicesByUserIdAndPushTokenNotNull(recipient.getId());
        if (usersDevices.isEmpty()) {
            log.warn("No devices found for userId: {}. Push notification wont be sent.", recipient.getId());
            return;
        }

        String finalTitle;
        String finalBody;
        Map<String, String> finalDataPayload;
        String finalTemplateName;
        Map<String, Object> finalTemplateParameters;

        if (sendPushToUserDto.getTemplateName()!= null){
            finalTemplateName = sendPushToUserDto.getTemplateName();
            finalTemplateParameters = sendPushToUserDto.getTemplateParameters();
            finalTitle = sendPushToUserDto.getTitle();
            finalBody = sendPushToUserDto.getBody();
            finalDataPayload = sendPushToUserDto.getDataPayload();
        } else if (sendPushToUserDto.getTitle() != null && sendPushToUserDto.getBody() != null) {
            finalTemplateName = "raw-push-template";
            finalTemplateParameters = Map.of("title", sendPushToUserDto.getTitle(), "body", sendPushToUserDto.getBody());
            finalTitle = sendPushToUserDto.getTitle();
            finalBody = sendPushToUserDto.getBody();
            finalDataPayload = sendPushToUserDto.getDataPayload() != null ? sendPushToUserDto.getDataPayload() : Map.of();
        }else {
            log.error("PushDTO is invalid. provide either template or direct content.");
            throw new ExternalServiceException(NotificationMessages.TEMPLATE_OR_CONTENT_NOT_PROVIDED);
        }

        for (Device device : usersDevices) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setSender(sender);
            notification.setChannel(NotificationChannel.PUSH);
            notification.setCategory(sendPushToUserDto.getCategory());
            notification.setDestinationDevice(device);
            notification.setStatus(NotificationStatus.QUEUED);
            notification.setSubject(finalTitle);
            notification.setBody(finalBody);
            notification.setTemplateName(finalTemplateName);
            notification.setPushNextAttemptAt(Instant.now());

            try{
                notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
                notification.setPushDataPayload(objectMapper.writeValueAsString(
                        finalDataPayload == null ? Map.of() : finalDataPayload));
            }catch (JacksonException e){
                log.error("Error while serializing template parameters for push notification with userId: {}", recipient.getId(), e);
                throw new ExternalServiceException("{parameters.could.not.serialize}");
            }

            var savedNotification = notificationDao.save(notification);

            PushTaskDto taskDto = new PushTaskDto(savedNotification.getId());


            prepareDispatch(savedNotification, taskDto, dispatchPriority);
        }

    }

    @Override
    @Transactional
    public void preparePushRecipients(Collection<UUID> recipientIds) {
        pushTokenMutationLock.lockUsers(recipientIds);
    }

    @Override
    public Notification getById(UUID id) {
        log.debug("Fetching notification by ID: {}", id);
        return notificationDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NotificationMessages.NOTIFICATION_NOT_FOUND_WITH_ID));
    }

    @Override
    public Notification save(Notification notification) {
        log.info("Saving notification with ID: {}", notification.getId());
        return notificationDao.save(notification);
    }

    @Override
    @Transactional
    public Optional<UUID> claimForDelivery(UUID notificationId, NotificationChannel channel) {
        Instant now = Instant.now();
        UUID claimId = UUID.randomUUID();
        if (notificationDao.claimDelivery(notificationId, channel.name(), claimId, now,
                now.minus(dispatchProperties.getDeliveryClaimTimeout())) == 1) {
            return Optional.of(claimId);
        }
        Notification notification = notificationDao.findById(notificationId).orElse(null);
        if (notification != null && notification.getChannel() == channel
                && (notification.getStatus() == NotificationStatus.QUEUED
                || notification.getStatus() == NotificationStatus.PROCESSING)) {
            throw new NotificationDeliveryBusyException();
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void completeDelivery(UUID notificationId, NotificationChannel channel, UUID claimId,
                                 String externalId) {
        requireDeliveryUpdate(notificationDao.completeDelivery(notificationId, channel.name(), claimId,
                externalId, Instant.now()));
    }

    @Override
    @Transactional
    public void failDelivery(UUID notificationId, NotificationChannel channel, UUID claimId, String reason) {
        requireDeliveryUpdate(notificationDao.failDelivery(notificationId, channel.name(), claimId,
                reason, Instant.now()));
    }

    @Override
    @Transactional
    public void suppressDelivery(UUID notificationId, NotificationChannel channel, UUID claimId, String reason) {
        requireDeliveryUpdate(notificationDao.suppressDelivery(notificationId, channel.name(), claimId,
                reason, Instant.now()));
    }

    @Override
    @Transactional
    public void releaseDeliveryForRetry(UUID notificationId, NotificationChannel channel, UUID claimId,
                                        String reason) {
        requireDeliveryUpdate(notificationDao.releaseDeliveryForRetry(notificationId, channel.name(), claimId,
                reason, Instant.now()));
    }

    @Override
    @Transactional
    public void terminalizeDelivery(UUID notificationId, NotificationChannel channel, String reason) {
        if (notificationDao.terminalizeDelivery(notificationId, channel.name(), reason, Instant.now()) == 1) {
            return;
        }
        Notification notification = notificationDao.findById(notificationId).orElse(null);
        if (notification == null || notification.getChannel() != channel
                || notification.getStatus() != NotificationStatus.FAILED) {
            throw new IllegalStateException("Notification delivery could not be terminalized");
        }
    }

    private void requireDeliveryUpdate(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("Notification delivery claim was lost");
        }
    }

    @Override
    @Transactional
    public void setAsOpened(UUID id) {
        log.info("Setting notification with ID: {} as opened", id);
        if (notificationDao.markDeliveredEmailAsOpened(id, Instant.now()) == 1) {
            log.info("Notification with ID: {} set as opened", id);
            return;
        }
        if (!notificationDao.existsById(id)) {
            throw new ResourceNotFoundException(NotificationMessages.NOTIFICATION_NOT_FOUND_WITH_ID);
        }
        log.debug("Notification with ID: {} was not eligible to be marked as opened", id);
    }


    private void prepareDispatch(Notification notification, Object payload, DispatchPriority dispatchPriority) {
        try {
            NotificationDispatchLogContext.capture(notification);
            notification.setDispatchPayload(objectMapper.writeValueAsString(payload));
            notification.setDispatchPriority(dispatchPriority);
            notification.setNextDispatchAt(Instant.now());
            notificationDao.save(notification);
        } catch (JacksonException exception) {
            throw new ExternalServiceException("{notification.dispatch.payload.could.not.serialize}");
        }
    }

    @Override
    @Transactional
    public Notification sendInApp(SendInAppDto sendInAppDto) {
        if (suppressed(sendInAppDto.getRecipient(), sendInAppDto.getCategory(), NotificationChannel.IN_APP, null)) {
            return null;
        }

        Notification notification = new Notification();
        notification.setRecipient(sendInAppDto.getRecipient());
        notification.setSender(securityService.getSystemUser());
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setCategory(sendInAppDto.getCategory());
        notification.setSubject(sendInAppDto.getSubject());
        notification.setBody(sendInAppDto.getBody());
        notification.setTargetType(sendInAppDto.getTargetType());
        notification.setTargetId(sendInAppDto.getTargetId());
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());

        Notification saved = notificationDao.save(notification);
        log.info("In-app notification {} recorded for user {}", saved.getId(), sendInAppDto.getRecipient().getId());
        return saved;
    }

    @Override
    @Transactional
    public void markRead(UUID notificationId) {
        UUID recipientId = securityService.getAuthenticatedUserId();

        notificationDao.findById(notificationId)
                .filter(notification -> notification.getRecipient().getId().equals(recipientId))
                .ifPresent(notification -> {
                    if (notification.getReadAt() == null) {
                        notification.setReadAt(Instant.now());
                        notificationDao.save(notification);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getMyNotifications(Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, NOTIFICATION_SORTABLE);
        return notificationDao.findAllByRecipientIdAndChannelOrderByCreatedAtDesc(
                securityService.getAuthenticatedUserId(), NotificationChannel.IN_APP,
                pageableSanitizer.sanitize(requested, NOTIFICATION_SORTABLE, NOTIFICATION_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public long myUnreadCount() {
        return notificationDao.countByRecipientIdAndChannelAndReadAtIsNull(securityService.getAuthenticatedUserId(), NotificationChannel.IN_APP);
    }

    @Override
    @Transactional
    public int markAllRead() {
        return notificationDao.markAllRead(securityService.getAuthenticatedUserId(), NotificationChannel.IN_APP, Instant.now());
    }
}

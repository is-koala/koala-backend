package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.NotificationMessages;
import com.exskylab.koala.core.dtos.notification.request.*;
import com.exskylab.koala.core.exceptions.*;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationManager implements NotificationService {

    private final NotificationDao notificationDao;
    private final NotificationProducerService notificationProducerService;
    private final Logger logger = LoggerFactory.getLogger(NotificationManager.class);
    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final SecurityService securityService;

    public NotificationManager(NotificationDao notificationDao,
                                NotificationProducerService notificationProducerService,
                               DeviceService deviceService,
                                 ObjectMapper objectMapper,
                               SecurityService securityService) {
        this.notificationDao = notificationDao;
        this.notificationProducerService = notificationProducerService;
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.securityService = securityService;
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

        logger.info("Sending email notification to {} destinationEmail: {}", recipientInfo, LogMasks.email(sendEmailDto.getDestinationEmail()));


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
            finalTemplateParameters = sendEmailDto.getTemplateParameters();
            finalSubject = sendEmailDto.getSubject();
        } else if (sendEmailDto.getHtmlBody() != null && sendEmailDto.getSubject() != null) {
            finalTemplateName = "raw-html-template";
            finalTemplateParameters = Map.of("htmlContent", sendEmailDto.getHtmlBody());
            finalSubject = sendEmailDto.getSubject();
        } else {
            logger.error("Email notification could not be queued for {}. Cause: {}",
                    recipientInfo, NotificationMessages.TEMPLATE_OR_SUBJECT_NOT_PROVIDED);
            throw new NotificationDispatchException(NotificationMessages.TEMPLATE_OR_SUBJECT_NOT_PROVIDED);
        }

        Notification notification = new Notification();
        notification.setRecipient(sendEmailDto.getRecipient());
        notification.setSender(sender);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(sendEmailDto.getCategory());
        notification.setDestinationEmail(sendEmailDto.getDestinationEmail());
        notification.setSubject(finalSubject);
        notification.setStatus(NotificationStatus.QUEUED);

        try {
            notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
        } catch (JacksonException e) {
            logger.error("Error while serializing template parameters for email with {} ErrorMessage: {}", recipientInfo, e.getMessage());
            throw new TemplateParametersSerializationException("{parameters.could.not.serialize}");
        }

        var savedNotification = notificationDao.save(notification);

        EmailTaskDto taskDto = new EmailTaskDto(
                savedNotification.getId(),
                savedNotification.getDestinationEmail(),
                finalSubject,
                finalTemplateName,
                finalTemplateParameters
        );

        executeDispatch(
                () -> notificationProducerService.sendEmailTask(taskDto),
                NotificationChannel.EMAIL,
                dispatchPriority,
                savedNotification.getId()
        );

        return savedNotification;
    }

    @Override
    @Transactional
    public Notification sendSms(SendSmsDto sendSmsDto, DispatchPriority dispatchPriority, boolean isSystemSms) {
        logger.info("Sending SMS notification to userId: {} destinationPhoneNumber: {}", sendSmsDto.getRecipient().getId(), LogMasks.phone(sendSmsDto.getDestinationPhoneNumber()));

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
            logger.error("SMS notification could not be queued for userId: {}. Cause: {}",
                    sendSmsDto.getRecipient().getId(), NotificationMessages.TEMPLATE_OR_MESSAGE_BODY_NOT_PROVIDED);
            throw new NotificationDispatchException(NotificationMessages.TEMPLATE_OR_MESSAGE_BODY_NOT_PROVIDED);
        }


        Notification notification = new Notification();
        notification.setRecipient(sendSmsDto.getRecipient());
        notification.setSender(sender);
        notification.setChannel(NotificationChannel.SMS);
        notification.setCategory(sendSmsDto.getCategory());
        notification.setDestinationPhoneNumber(sendSmsDto.getDestinationPhoneNumber());
        notification.setStatus(NotificationStatus.QUEUED);

        try {
            notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
        } catch (JacksonException e) {
            logger.error("Error while serializing template parameters for email with userId: {} ErrorMessage: {}", sendSmsDto.getRecipient().getId(), e.getMessage());
            throw new TemplateParametersSerializationException("{parameters.could.not.serialize}");
        }

        var savedNotification = notificationDao.save(notification);
        SmsTaskDto taskDto = new SmsTaskDto(
                savedNotification.getId(),
                savedNotification.getDestinationPhoneNumber(),
                finalTemplateName,
                finalTemplateParameters
        );

        executeDispatch(
                () -> notificationProducerService.sendSmsTask(taskDto),
                NotificationChannel.SMS,
                dispatchPriority,
                savedNotification.getId()
        );

        return savedNotification;

    }

    @Override
    @Transactional
    public void sendPushToUser(SendPushToUserDto sendPushToUserDto, DispatchPriority dispatchPriority, boolean isSystemPush) {
        logger.info("Dispatching push notification to userId: {} with title: {}",
                sendPushToUserDto.getRecipient().getId(), sendPushToUserDto.getTitle());

        if (sendPushToUserDto.getRecipient() == null) {
            throw new ArgumentCannotBeNullException(NotificationMessages.USER_ID_CANNOT_BE_NULL);
        }

        User sender = isSystemPush ? securityService.getSystemUser(): securityService.getAuthenticatedUser();
        User recipient = sendPushToUserDto.getRecipient();

        List<Device> usersDevices = deviceService.getDevicesByUserIdAndPushTokenNotNull(recipient.getId());
        if (usersDevices.isEmpty()) {
            logger.warn("No devices found for userId: {}. Push notification wont be sent.", recipient.getId());
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
            logger.error("PushDTO is invalid. provide either template or direct content.");
            throw new NotificationDispatchException(NotificationMessages.TEMPLATE_OR_CONTENT_NOT_PROVIDED);
        }

        for (Device device : usersDevices) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setSender(sender);
            notification.setChannel(NotificationChannel.PUSH);
            notification.setCategory(sendPushToUserDto.getCategory());
            notification.setDestinationPushToken(device.getPushToken());
            notification.setStatus(NotificationStatus.QUEUED);
            notification.setSubject(finalTitle);
            notification.setTemplateParameters(finalTemplateName);

            try{
                notification.setTemplateParameters(objectMapper.writeValueAsString(finalTemplateParameters));
            }catch (JacksonException e){
                logger.error("Error while serializing template parameters for push notification with userId: {} ErrorMessage: {}", recipient.getId(), e.getMessage());
                throw new TemplateParametersSerializationException("{parameters.could.not.serialize}");
            }

            var savedNotification = notificationDao.save(notification);

            PushTaskDto taskDto = new PushTaskDto(
                    savedNotification.getId(),
                    savedNotification.getDestinationPushToken(),
                    finalTitle,
                    finalBody,
                    finalDataPayload
            );


            executeDispatch(
                    () -> notificationProducerService.sendPushTask(taskDto),
                    NotificationChannel.PUSH,
                    dispatchPriority,
                    savedNotification.getId()
            );
        }

    }

    @Override
    public Notification sendPushToDevice(SendPushToDeviceDto sendPushToDeviceDto, DispatchPriority dispatchPriority, boolean isSystemPush) {
        return null; //TODO: Implement this method
    }

    @Override
    public void sendBulkPush(SendBulkPushDto sendBulkPushDto, DispatchPriority dispatchPriority, boolean isSystemPush) {
            //TODO: Implement this method
    }

    @Override
    public Notification getById(UUID id) {
        logger.debug("Fetching notification by ID: {}", id);
        return notificationDao.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(NotificationMessages.NOTIFICATION_NOT_FOUND_WITH_ID));
    }

    @Override
    public Notification save(Notification notification) {
        logger.info("Saving notification with ID: {}", notification.getId());
        return notificationDao.save(notification);
    }

    @Override
    public void setAsOpened(UUID id) {
        var notification = notificationDao.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(NotificationMessages.NOTIFICATION_NOT_FOUND_WITH_ID));
        logger.info("Setting notification with ID: {} as opened", id);
        notification.setStatus(NotificationStatus.OPENED);
        notificationDao.save(notification);
        logger.info("Notification with ID: {} set as opened", id);
    }


    private void executeDispatch(Runnable dispatchAction, NotificationChannel channel, DispatchPriority dispatchPriority,
                                 UUID id) {
        try {
            dispatchAction.run();
        } catch (TaskCannotBeSentException e) {
            logger.warn("Dispatch to queue failed for notification id: {}, Priority: {}. Error: {}"
                    , id, dispatchPriority, e.getMessage());


            if (dispatchPriority == DispatchPriority.CRITICAL) {
                switch (channel) {
                    case EMAIL -> {
                        logger.error("Critical email notification could not be queued for notification id: {}. " +
                                "The process will stop.", id);
                        throw new NotificationDispatchException("{critical.email.dispatch.failed}");
                    }
                    case SMS -> {
                        logger.error("Critical SMS notification could not be queued for notification id: {}. " +
                                "The process will stop.", id);
                        throw new NotificationDispatchException("{critical.sms.dispatch.failed}");
                    }
                    case PUSH -> {
                        logger.error("Critical push notification could not be queued for notification id: {}. " +
                                "The process will stop.", id);
                        throw new NotificationDispatchException("{critical.push.dispatch.failed}");
                    }
                    default -> throw new InvalidCategoryException("{invalid.notification.channel}");
                }

            }

            logger.error("Non-critical notification could not be queued for notification id: {}. " +
                    "The process will continue. A cleanup job might handle this.", id);

        }
    }

/*
    private Locale determineLocale(String requestedLanguage, User recipient){
        if (requestedLanguage != null && !requestedLanguage.isBlank()){
            logger.debug("Users requested language for notification: {}", requestedLanguage);
            return Locale.forLanguageTag(requestedLanguage);
        }

        logger.debug("No language specified for notification. Using default language for user: {}", recipient.get());
        return null;
    }

 */

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getMyNotifications(Pageable pageable) {
        return notificationDao.findAllByRecipientIdOrderByCreatedAtDesc(
                securityService.getAuthenticatedUserId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long myUnreadCount() {
        return notificationDao.countByRecipientIdAndReadAtIsNull(securityService.getAuthenticatedUserId());
    }

    @Override
    @Transactional
    public int markAllRead() {
        return notificationDao.markAllRead(securityService.getAuthenticatedUserId(), Instant.now());
    }
}

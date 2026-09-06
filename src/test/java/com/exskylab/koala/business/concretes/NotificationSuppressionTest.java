package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSuppressionTest {

    private NotificationDao notificationDao;
    private NotificationProducerService producer;
    private NotificationPreferenceService preferences;
    private IysService iysService;
    private NotificationManager manager;
    private User recipient;

    @BeforeEach
    void setUp() {
        notificationDao = mock(NotificationDao.class);
        producer = mock(NotificationProducerService.class);
        preferences = mock(NotificationPreferenceService.class);
        when(preferences.isAllowed(any(), any(), any())).thenReturn(true);
        recipient = new User();
        recipient.setId(UUID.randomUUID());
        recipient.setFirstName("Ada");
        recipient.setEmail("ada@koala.test");

        iysService = mock(IysService.class);
        when(iysService.isAllowed(eq(recipient), eq(NotificationChannel.EMAIL), eq(recipient.getEmail())))
                .thenReturn(true);

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUserId()).thenReturn(recipient.getId());
        when(security.getSystemUser()).thenReturn(recipient);

        Device device = new Device();
        device.setPushToken("push-token");
        DeviceService devices = mock(DeviceService.class);
        when(devices.getDevicesByUserIdAndPushTokenNotNull(any())).thenReturn(List.of(device));

        UnsubscribeTokenService unsubscribeTokens = mock(UnsubscribeTokenService.class);
        when(unsubscribeTokens.issueLink(any(), any(), any()))
                .thenReturn("https://iskoala.com/bildirim-tercihleri/cikis?token=abc");

        manager = new NotificationManager(notificationDao, producer, devices,
                new ObjectMapper(), security, new PageableSanitizer(),
                preferences, unsubscribeTokens, iysService);

        when(notificationDao.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private void switchedOff(NotificationCategory category, NotificationChannel channel) {
        when(preferences.isAllowed(eq(recipient.getId()), eq(category), eq(channel))).thenReturn(false);
    }

    private SendEmailDto email(NotificationCategory category) {
        SendEmailDto dto = new SendEmailDto();
        dto.setRecipient(recipient);
        dto.setDestinationEmail(recipient.getEmail());
        dto.setCategory(category);
        dto.setSubject("Yeni ilanlar");
        dto.setTemplateName("activity-notice-template");
        dto.setTemplateParameters(new HashMap<>(Map.of("firstName", "Ada")));
        return dto;
    }

    private SendPushToUserDto push(NotificationCategory category) {
        SendPushToUserDto dto = new SendPushToUserDto();
        dto.setRecipient(recipient);
        dto.setCategory(category);
        dto.setTitle("Yeni ilan");
        dto.setBody("Barista aranıyor");
        return dto;
    }

    private SendInAppDto inApp(NotificationCategory category) {
        SendInAppDto dto = new SendInAppDto();
        dto.setRecipient(recipient);
        dto.setCategory(category);
        dto.setSubject("Yeni ilan");
        dto.setBody("Barista aranıyor");
        return dto;
    }

    private EmailTaskDto queuedEmail() {
        ArgumentCaptor<EmailTaskDto> captor = ArgumentCaptor.forClass(EmailTaskDto.class);
        verify(producer).sendEmailTask(captor.capture());
        return captor.getValue();
    }

    @Test
    void aJobAlertEmailIsNotQueuedOnceTheUserSwitchesThatChannelOff() {
        switchedOff(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL);

        assertNull(manager.sendEmail(email(NotificationCategory.JOB_ALERTS), DispatchPriority.NORMAL, true));

        verify(producer, never()).sendEmailTask(any());
        verify(notificationDao, never()).save(any());
    }

    @Test
    void switchingOffTheEmailChannelLeavesThePushForTheSameCategoryAlone() {
        switchedOff(NotificationCategory.JOB_ALERTS, NotificationChannel.EMAIL);

        manager.sendPushToUser(push(NotificationCategory.JOB_ALERTS), DispatchPriority.NORMAL, true);

        verify(producer).sendPushTask(any());
    }

    @Test
    void aSwitchedOffPushIsNeitherQueuedNorRecorded() {
        switchedOff(NotificationCategory.JOB_ALERTS, NotificationChannel.PUSH);

        manager.sendPushToUser(push(NotificationCategory.JOB_ALERTS), DispatchPriority.NORMAL, true);

        verify(producer, never()).sendPushTask(any());
        verify(notificationDao, never()).save(any());
    }

    @Test
    void aSwitchedOffInAppCategoryLeavesNoFeedEntry() {
        switchedOff(NotificationCategory.MARKETINGS, NotificationChannel.IN_APP);

        assertNull(manager.sendInApp(inApp(NotificationCategory.MARKETINGS)));

        verify(notificationDao, never()).save(any());
    }

    @Test
    void aPaymentNotificationIsDeliveredNoMatterWhatThePreferencesSay() {
        when(preferences.isAllowed(any(), any(), any())).thenReturn(true);

        manager.sendEmail(email(NotificationCategory.APPLICATION_UPDATES), DispatchPriority.NORMAL, true);

        verify(producer).sendEmailTask(any());
    }

    @Test
    void anEmailToAnAddressWithoutAKoalaAccountIsNeverSuppressed() {
        SendEmailDto toCompanyAddress = email(NotificationCategory.COMPANY_UPDATES);
        toCompanyAddress.setRecipient(null);

        manager.sendEmail(toCompanyAddress, DispatchPriority.NORMAL, true);

        verify(producer).sendEmailTask(any());
    }

    @Test
    void everyCommercialEmailCarriesAWorkingUnsubscribeLink() {
        manager.sendEmail(email(NotificationCategory.NEWSLETTER), DispatchPriority.NORMAL, true);

        assertEquals("https://iskoala.com/bildirim-tercihleri/cikis?token=abc",
                queuedEmail().templateParameters().get("unsubscribeUrl"));
    }

    @Test
    void aTransactionalEmailOffersNoUnsubscribeLinkBecauseItCannotBeSwitchedOff() {
        manager.sendEmail(email(NotificationCategory.ACCOUNT_SECURITY), DispatchPriority.NORMAL, true);

        assertFalse(queuedEmail().templateParameters().containsKey("unsubscribeUrl"));
    }

    @Test
    void aCompanyInvitationIsQueuedAndCarriesNoUnsubscribeLink() {
        manager.sendEmail(email(NotificationCategory.COMPANY_UPDATES), DispatchPriority.CRITICAL, true);

        assertFalse(queuedEmail().templateParameters().containsKey("unsubscribeUrl"),
                "yetkili daveti eyleme çağıran bir posta, çıkış bağlantısı taşımamalı");
    }

    @Test
    void aJobAlertEmailOffersNoUnsubscribeLinkBecauseItIsNotACommercialMessage() {
        manager.sendEmail(email(NotificationCategory.JOB_ALERTS), DispatchPriority.NORMAL, true);

        assertFalse(queuedEmail().templateParameters().containsKey("unsubscribeUrl"));
    }

    @Test
    void theUnsubscribeLinkNamesTheCategoryAndChannelOfTheMailItRidesOn() {
        UnsubscribeTokenService unsubscribeTokens = mock(UnsubscribeTokenService.class);
        when(unsubscribeTokens.issueLink(any(), any(), any())).thenReturn("https://iskoala.com/x?token=y");

        manager = new NotificationManager(notificationDao, producer,
                mock(DeviceService.class), new ObjectMapper(),
                mock(SecurityService.class), new PageableSanitizer(), preferences, unsubscribeTokens, iysService);

        manager.sendEmail(email(NotificationCategory.NEWSLETTER), DispatchPriority.NORMAL, true);

        verify(unsubscribeTokens).issueLink(recipient, NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL);
    }

    @Test
    void aSuppressedEmailNeverAsksForAnUnsubscribeToken() {
        UnsubscribeTokenService unsubscribeTokens = mock(UnsubscribeTokenService.class);
        manager = new NotificationManager(notificationDao, producer,
                mock(DeviceService.class), new ObjectMapper(),
                mock(SecurityService.class), new PageableSanitizer(), preferences, unsubscribeTokens, iysService);
        switchedOff(NotificationCategory.NEWSLETTER, NotificationChannel.EMAIL);

        manager.sendEmail(email(NotificationCategory.NEWSLETTER), DispatchPriority.NORMAL, true);

        verify(unsubscribeTokens, never()).issueLink(any(), any(), any());
    }

    @Test
    void aCommercialEmailWithoutAKnownRecipientIsSuppressedByIys() {
        SendEmailDto newsletter = email(NotificationCategory.NEWSLETTER);
        newsletter.setRecipient(null);

        assertNull(manager.sendEmail(newsletter, DispatchPriority.NORMAL, true));

        verify(producer, never()).sendEmailTask(any());
    }

    @Test
    void aCommercialEmailCannotBorrowAnotherRecipientsIysPermission() {
        SendEmailDto newsletter = email(NotificationCategory.NEWSLETTER);
        newsletter.setDestinationEmail("other@koala.test");

        assertNull(manager.sendEmail(newsletter, DispatchPriority.NORMAL, true));

        verify(producer, never()).sendEmailTask(any());
    }
}

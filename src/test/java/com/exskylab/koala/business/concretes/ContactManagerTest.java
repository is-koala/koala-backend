package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.contact.request.ContactRequestDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.properties.SupportInboxProperties;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ContactManagerTest {

    private NotificationService notifications;
    private ContactManager manager;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        SupportInboxProperties inbox = new SupportInboxProperties();
        inbox.setMailbox("info@iskoala.com");
        manager = new ContactManager(notifications, inbox);
    }

    @Test
    void aValidSubmissionIsQueuedToTheSupportMailboxWithReplyTo() {
        manager.submit(request("kvkk", "Ada Yılmaz", "ada@example.test",
                "Verilerimin silinmesini istiyorum.", true, ""));

        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notifications).sendEmail(captor.capture(), eq(DispatchPriority.NORMAL), eq(true));
        SendEmailDto mail = captor.getValue();
        assertEquals("info@iskoala.com", mail.getDestinationEmail());
        assertEquals(NotificationCategory.ACCOUNT_SECURITY, mail.getCategory());
        assertEquals("[Koala] KVKK — Ada Yılmaz", mail.getSubject());
        assertEquals("contact-form-template", mail.getTemplateName());
        assertEquals("KVKK", mail.getTemplateParameters().get("topic"));
        assertEquals("Ada Yılmaz", mail.getTemplateParameters().get("name"));
        assertEquals("ada@example.test", mail.getTemplateParameters().get("email"));
        assertEquals("Verilerimin silinmesini istiyorum.", mail.getTemplateParameters().get("message"));
        assertEquals("ada@example.test", mail.getTemplateParameters().get("replyTo"));
        assertNull(mail.getRecipient());
    }

    @Test
    void aFilledHoneypotDoesNotQueueMail() {
        manager.submit(request("destek", "Ada Yılmaz", "ada@example.test",
                "Merhaba", true, "http://bot.test"));

        verifyNoInteractions(notifications);
    }

    @Test
    void headerInjectionInTheNameIsFlattenedBeforeTheSubject() {
        manager.submit(request("diger", "Ada\r\nBcc: stolen@example.test", "ada@example.test",
                "Merhaba", true, " "));

        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notifications).sendEmail(captor.capture(), any(), eq(true));
        assertEquals("[Koala] Diğer — Ada  Bcc: stolen@example.test", captor.getValue().getSubject());
    }

    private static ContactRequestDto request(String topic, String name, String email, String message,
                                             boolean consent, String website) {
        ContactRequestDto dto = new ContactRequestDto();
        dto.setTopic(topic);
        dto.setName(name);
        dto.setEmail(email);
        dto.setMessage(message);
        dto.setConsent(consent);
        dto.setWebsite(website);
        return dto;
    }
}

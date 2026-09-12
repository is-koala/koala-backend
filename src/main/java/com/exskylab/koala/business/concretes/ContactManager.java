package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.ContactService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.contact.request.ContactRequestDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.properties.SupportInboxProperties;
import com.exskylab.koala.entities.ContactTopic;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
public class ContactManager implements ContactService {

    private final NotificationService notificationService;
    private final SupportInboxProperties inboxProperties;

    public ContactManager(NotificationService notificationService, SupportInboxProperties inboxProperties) {
        this.notificationService = notificationService;
        this.inboxProperties = inboxProperties;
    }

    @Override
    @Transactional
    public void submit(ContactRequestDto request) {
        if (honeypotFilled(request.getWebsite())) {
            return;
        }

        ContactTopic topic = ContactTopic.fromWire(request.getTopic());
        String name = headerSafe(request.getName());
        String email = headerSafe(request.getEmail());
        String message = request.getMessage().strip();

        SendEmailDto dto = new SendEmailDto();
        dto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        dto.setDestinationEmail(inboxProperties.getMailbox());
        dto.setSubject("[Koala] " + topic.label() + " — " + name);
        dto.setTemplateName("contact-form-template");
        dto.setTemplateParameters(Map.of(
                "topic", topic.label(),
                "name", name,
                "email", email,
                "message", message,
                "replyTo", email));

        log.info("Queued contact form mail topic={} from {}", topic.wireValue(), LogMasks.email(email));
        notificationService.sendEmail(dto, DispatchPriority.NORMAL, true);
    }

    private static boolean honeypotFilled(String website) {
        return website != null && !website.isBlank();
    }

    /**
     * Konu ve Reply-To başlığına CR/LF girmesin diye. Gövde şablonda th:text
     * ile kaçar; başlık kaçmaz.
     */
    private static String headerSafe(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').strip();
    }
}

package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.PasswordResetNotificationService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PasswordResetNotificationManager implements PasswordResetNotificationService {

    private final UserService userService;
    private final NotificationService notificationService;

    public PasswordResetNotificationManager(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCompletionEmail(UUID userId) {
        User user = userService.getById(userId);

        SendEmailDto email = new SendEmailDto();
        email.setRecipient(user);
        email.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        email.setDestinationEmail(user.getEmail());
        email.setSubject("Koala parolan değiştirildi");
        email.setTemplateName("password-reset-completed-template");
        email.setTemplateParameters(Map.of(
                "firstName", user.getFirstName() == null ? "" : user.getFirstName(),
                "lastName", user.getLastName() == null ? "" : user.getLastName()));

        notificationService.sendEmail(email, DispatchPriority.NORMAL, true);
    }
}

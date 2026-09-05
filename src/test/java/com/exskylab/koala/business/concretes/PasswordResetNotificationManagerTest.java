package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetNotificationManagerTest {

    @Test
    void completionEmailUsesItsOwnSecurityTemplateAndTransaction() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("ada@example.com");
        user.setFirstName("Ada");
        user.setLastName("Yılmaz");

        UserService users = mock(UserService.class);
        NotificationService notifications = mock(NotificationService.class);
        when(users.getById(userId)).thenReturn(user);

        new PasswordResetNotificationManager(users, notifications).sendCompletionEmail(userId);

        ArgumentCaptor<SendEmailDto> email = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notifications).sendEmail(email.capture(), org.mockito.ArgumentMatchers.eq(DispatchPriority.NORMAL),
                org.mockito.ArgumentMatchers.eq(true));
        assertEquals(NotificationCategory.ACCOUNT_SECURITY, email.getValue().getCategory());
        assertEquals("password-reset-completed-template", email.getValue().getTemplateName());
        assertEquals("ada@example.com", email.getValue().getDestinationEmail());

        Method method = PasswordResetNotificationManager.class
                .getMethod("sendCompletionEmail", UUID.class);
        assertEquals(Propagation.REQUIRES_NEW, method.getAnnotation(Transactional.class).propagation());
    }
}

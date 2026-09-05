package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class NotificationPreferenceIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private CommercialMessageConsentDao commercialMessageConsentDao;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationProducerService notificationProducerService;

    @Test
    void aWorkerWhoSwitchesOffJobAlertEmailsGenuinelyStopsReceivingThem() throws Exception {
        User quiet = saveUser("job-alert-off@koala.test");
        User loud = saveUser("job-alert-on@koala.test");

        switchOff(login("job-alert-off@koala.test"), "JOB_ALERTS", "EMAIL");

        assertNull(notificationService.sendEmail(
                jobAlertEmail(quiet), DispatchPriority.NORMAL, true));
        verify(notificationProducerService, never()).sendEmailTask(any());

        assertNotNull(notificationService.sendEmail(
                jobAlertEmail(loud), DispatchPriority.NORMAL, true));
        verify(notificationProducerService).sendEmailTask(any());
    }

    @Test
    void switchingOffJobAlertEmailsLeavesTheSameAlertsPushAlone() throws Exception {
        saveUser("job-alert-channels@koala.test");
        String token = login("job-alert-channels@koala.test");

        switchOff(token, "JOB_ALERTS", "EMAIL");

        assertFalse(enabledFor(token, "JOB_ALERTS", "EMAIL"));
        assertTrue(enabledFor(token, "JOB_ALERTS", "PUSH"));
        assertTrue(enabledFor(token, "JOB_ALERTS", "IN_APP"));
    }

    @Test
    void transactionalNotificationsCannotBeSwitchedOff() throws Exception {
        User payer = saveUser("transactional@koala.test");
        String token = login("transactional@koala.test");

        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("APPLICATION_UPDATES", "EMAIL", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"));

        SendEmailDto payment = jobAlertEmail(payer);
        payment.setCategory(NotificationCategory.APPLICATION_UPDATES);

        assertNotNull(notificationService.sendEmail(payment, DispatchPriority.NORMAL, true));
        verify(notificationProducerService).sendEmailTask(any());
    }

    @Test
    void theUnsubscribeLinkInsideTheEmailWorksWithoutLoggingInAndOnlyClosesItsOwnChannel() throws Exception {
        User reader = saveUser("one-click@koala.test");
        String optIn = login("one-click@koala.test");

        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + optIn)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferences":[
                                  {"category":"NEWSLETTER","channel":"EMAIL","enabled":true},
                                  {"category":"NEWSLETTER","channel":"PUSH","enabled":true}]}
                                """))
                .andExpect(status().isOk());

        SendEmailDto newsletter = jobAlertEmail(reader);
        newsletter.setCategory(NotificationCategory.NEWSLETTER);
        notificationService.sendEmail(newsletter, DispatchPriority.NORMAL, true);

        ArgumentCaptor<EmailTaskDto> captor = ArgumentCaptor.forClass(EmailTaskDto.class);
        verify(notificationProducerService).sendEmailTask(captor.capture());
        String unsubscribeUrl = String.valueOf(captor.getValue().templateParameters().get("unsubscribeUrl"));

        assertTrue(unsubscribeUrl.contains("/bildirim-tercihleri/cikis?token="), unsubscribeUrl);
        String rawToken = unsubscribeUrl.substring(unsubscribeUrl.indexOf("token=") + "token=".length());

        mockMvc.perform(post("/notification-preferences/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        String token = login("one-click@koala.test");

        assertFalse(enabledFor(token, "NEWSLETTER", "EMAIL"));
        assertTrue(enabledFor(token, "NEWSLETTER", "PUSH"));
        assertTrue(enabledFor(token, "JOB_ALERTS", "EMAIL"));
    }

    @Test
    void anUnknownUnsubscribeTokenIsRefused() throws Exception {
        mockMvc.perform(post("/notification-preferences/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"uydurma-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_UNSUBSCRIBE_TOKEN_INVALID"));
    }

    @Test
    void optingIntoCommercialMessagesLeavesAConsentTrailWithTheChannelAndTheIpAddress() throws Exception {
        User subscriber = saveUser("commercial@koala.test");
        String token = login("commercial@koala.test");

        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("MARKETINGS", "EMAIL", true)))
                .andExpect(status().isOk());

        List<CommercialMessageConsent> trail =
                commercialMessageConsentDao.findAllByUserIdOrderByRecordedAtDesc(subscriber.getId());

        assertEquals(1, trail.size());
        assertTrue(trail.getFirst().isGranted());
        assertEquals(NotificationCategory.MARKETINGS, trail.getFirst().getCategory());
        assertNotNull(trail.getFirst().getRecordedAt());
        assertNotNull(trail.getFirst().getIpAddress());
    }

    @Test
    void aCommercialMessageIsNotSentUntilTheUserAsksForIt() throws Exception {
        User subscriber = saveUser("commercial-default@koala.test");

        SendEmailDto newsletter = jobAlertEmail(subscriber);
        newsletter.setCategory(NotificationCategory.NEWSLETTER);

        assertNull(notificationService.sendEmail(newsletter, DispatchPriority.NORMAL, true));
        verify(notificationProducerService, never()).sendEmailTask(any());

        String token = login("commercial-default@koala.test");
        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("NEWSLETTER", "EMAIL", true)))
                .andExpect(status().isOk());

        assertNotNull(notificationService.sendEmail(newsletter, DispatchPriority.NORMAL, true));
        verify(notificationProducerService).sendEmailTask(any());
    }

    private boolean enabledFor(String accessToken, String category, String channel) throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        for (var entry : objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data")) {
            if (category.equals(entry.get("category").asString())
                    && channel.equals(entry.get("channel").asString())) {
                return entry.get("enabled").asBoolean();
            }
        }
        throw new AssertionError(category + "/" + channel + " tercih listesinde yok");
    }

    private SendEmailDto jobAlertEmail(User recipient) {
        SendEmailDto dto = new SendEmailDto();
        dto.setRecipient(recipient);
        dto.setDestinationEmail(recipient.getEmail());
        dto.setCategory(NotificationCategory.JOB_ALERTS);
        dto.setSubject("Sana uygun yeni ilanlar");
        dto.setTemplateName("activity-notice-template");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("firstName", recipient.getFirstName());
        parameters.put("message", "Barista aranıyor");
        dto.setTemplateParameters(parameters);
        return dto;
    }

    private void switchOff(String accessToken, String category, String channel) throws Exception {
        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(category, channel, false)))
                .andExpect(status().isOk());
    }

    private String body(String category, String channel, boolean enabled) {
        return """
                {"preferences":[{"category":"%s","channel":"%s","enabled":%s}]}
                """.formatted(category, channel, enabled);
    }

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yılmaz");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("GecerliParola1"));
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        return userDao.saveAndFlush(user);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "notification-preference-it-device")
                        .header("User-Agent", "Koala Notification Preference IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"GecerliParola1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("token").asText();
    }

    @Test
    void thePreferenceSurfaceIsClosedToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/users/me/notification-preferences"))
                .andExpect(status().isUnauthorized());
    }
}

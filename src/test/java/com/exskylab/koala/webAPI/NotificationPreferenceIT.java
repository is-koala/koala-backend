package com.exskylab.koala.webAPI;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendSmsDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.security.TokenDigest;
import com.exskylab.koala.dataAccess.CommercialMessageConsentDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.CommercialConsentSource;
import com.exskylab.koala.entities.CommercialMessageConsent;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private UnsubscribeTokenService unsubscribeTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        verify(notificationProducerService, never()).sendEmailTask(any());
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
        verify(notificationProducerService, never()).sendEmailTask(any());
    }

    @Test
    void theUnsubscribeLinkWorksWithoutLoggingInAndOnlyClosesItsOwnChannel() throws Exception {
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

        String rawToken = issuedToken(reader, NotificationCategory.NEWSLETTER);

        MvcResult first = mockMvc.perform(unsubscribeWith(rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("NEWSLETTER"))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.alreadyDisabled").value(false))
                .andReturn();

        assertFalse(first.getResponse().getContentAsString().contains(rawToken),
                "basari yaniti ham tokeni yankilamamali");

        String token = login("one-click@koala.test");

        assertFalse(enabledFor(token, "NEWSLETTER", "EMAIL"));
        assertTrue(enabledFor(token, "NEWSLETTER", "PUSH"));
        assertTrue(enabledFor(token, "JOB_ALERTS", "EMAIL"));
    }

    @Test
    void clickingTheSameLinkAgainAnswersTheSameChannelAndWritesNothingNew() throws Exception {
        User reader = saveUser("two-clicks@koala.test");
        optIn(login("two-clicks@koala.test"), "NEWSLETTER", "EMAIL");
        String rawToken = issuedToken(reader, NotificationCategory.NEWSLETTER);

        mockMvc.perform(unsubscribeWith(rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyDisabled").value(false));
        commercialMessageConsentDao.flush();
        Instant firstUpdate = preferenceUpdatedAt(reader.getId());
        Integer tasksAfterFirstClick = iysTaskCount(reader.getId());

        mockMvc.perform(unsubscribeWith(rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category").value("NEWSLETTER"))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.alreadyDisabled").value(true));
        commercialMessageConsentDao.flush();

        List<CommercialMessageConsent> withdrawals = commercialMessageConsentDao
                .findAllByUserIdOrderByRecordedAtDesc(reader.getId()).stream()
                .filter(consent -> consent.getSource() == CommercialConsentSource.UNSUBSCRIBE_LINK)
                .toList();
        assertEquals(1, withdrawals.size(), "ikinci tik ikinci bir riza kaydi acmamali");
        assertEquals(firstUpdate, preferenceUpdatedAt(reader.getId()), "ikinci tik tercih satirini yeniden yazmamali");
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from notification_preferences where user_id = ?", Integer.class, reader.getId()));
        assertEquals(tasksAfterFirstClick, iysTaskCount(reader.getId()),
                "ikinci tik IYS kuyruguna yeni bir gorev acmamali");
        assertFalse(enabledFor(login("two-clicks@koala.test"), "NEWSLETTER", "EMAIL"));
    }

    @Test
    void anUnknownUnsubscribeTokenIsRefused() throws Exception {
        MvcResult refusal = mockMvc.perform(unsubscribeWith("uydurma-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_UNSUBSCRIBE_TOKEN_INVALID"))
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Abonelikten çıkma bağlantısı geçersiz. "
                        + "Bildirim tercihlerinizi uygulamadan yönetebilirsiniz."))
                .andReturn();

        assertFalse(refusal.getResponse().getContentAsString().contains("uydurma-token"),
                "hata yaniti ham tokeni yankilamamali");
    }

    @Test
    void aTamperedTokenLooksExactlyLikeAnUnknownOne() throws Exception {
        User reader = saveUser("tampered@koala.test");
        String rawToken = issuedToken(reader, NotificationCategory.NEWSLETTER);
        String tampered = rawToken.substring(0, rawToken.length() - 1) + (rawToken.endsWith("A") ? "B" : "A");

        String unknownBody = comparable(mockMvc.perform(unsubscribeWith("uydurma-token"))
                .andExpect(status().isBadRequest()).andReturn());
        String tamperedBody = comparable(mockMvc.perform(unsubscribeWith(tampered))
                .andExpect(status().isBadRequest()).andReturn());

        assertEquals(unknownBody, tamperedBody);
    }

    @Test
    void anExpiredUnsubscribeTokenIsRefusedWithItsOwnCode() throws Exception {
        User reader = saveUser("expired-link@koala.test");
        String rawToken = "suresi-dolmus-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into unsubscribe_tokens
                    (id, user_id, category, channel, token_digest, expires_at, created_at, is_deleted)
                values (?, ?, 'NEWSLETTER', 'EMAIL', ?, now() - interval '1 day', now() - interval '91 days', false)
                """, UUID.randomUUID(), reader.getId(), TokenDigest.of(rawToken));

        MvcResult refusal = mockMvc.perform(unsubscribeWith(rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_UNSUBSCRIBE_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Abonelikten çıkma bağlantısının süresi dolmuş. "
                        + "Daha yeni bir e-postadaki bağlantıyı kullanın ya da tercihlerinizi uygulamadan yönetin."))
                .andReturn();

        assertFalse(refusal.getResponse().getContentAsString().contains(rawToken),
                "hata yaniti ham tokeni yankilamamali");
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from notification_preferences where user_id = ?", Integer.class, reader.getId()));
    }

    @Test
    void anExpiredTokenAnswersInEnglishWhenAsked() throws Exception {
        User reader = saveUser("expired-link-en@koala.test");
        String rawToken = "suresi-dolmus-en-" + UUID.randomUUID();
        jdbcTemplate.update("""
                insert into unsubscribe_tokens
                    (id, user_id, category, channel, token_digest, expires_at, created_at, is_deleted)
                values (?, ?, 'NEWSLETTER', 'EMAIL', ?, now() - interval '1 day', now() - interval '91 days', false)
                """, UUID.randomUUID(), reader.getId(), TokenDigest.of(rawToken));

        mockMvc.perform(unsubscribeWith(rawToken).header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_UNSUBSCRIBE_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("This unsubscribe link has expired. "
                        + "Use the link in a more recent email or manage your preferences in the app."));
    }

    @Test
    void aBlankUnsubscribeTokenNeverReachesTheTokenLookup() throws Exception {
        mockMvc.perform(unsubscribeWith("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("token"))
                .andExpect(jsonPath("$.details[0].message").value("Abonelikten çıkma bağlantısı eksik. "
                        + "E-postadaki bağlantıyı yeniden açın."));
    }

    private String issuedToken(User reader, NotificationCategory category) {
        String unsubscribeUrl = unsubscribeTokenService.issueLink(reader, category, NotificationChannel.EMAIL);

        assertTrue(unsubscribeUrl.contains("/bildirim-tercihleri/cikis?token="), unsubscribeUrl);
        return unsubscribeUrl.substring(unsubscribeUrl.indexOf("token=") + "token=".length());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder unsubscribeWith(String rawToken) {
        return post("/notification-preferences/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\"}");
    }

    private void optIn(String accessToken, String category, String channel) throws Exception {
        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(category, channel, true)))
                .andExpect(status().isOk());
    }

    private Integer iysTaskCount(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from iys_consent_tasks where user_id = ?", Integer.class, userId);
    }

    private Instant preferenceUpdatedAt(UUID userId) {
        return jdbcTemplate.queryForObject("""
                select coalesce(updated_at, created_at) from notification_preferences
                where user_id = ? and category = 'NEWSLETTER' and channel = 'EMAIL'
                """, java.sql.Timestamp.class, userId).toInstant();
    }

    private String comparable(MvcResult result) throws Exception {
        var body = (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(
                result.getResponse().getContentAsByteArray());
        body.remove("timeStamp");
        body.remove("requestId");
        return body.toString();
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
    void aCommercialPreferenceUpdateCommitsAnIysOutboxTaskWithoutWaitingForTheProvider() throws Exception {
        User subscriber = saveUser("commercial-outbox@koala.test");
        String token = login("commercial-outbox@koala.test", "WEB");

        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Client-Platform", "WEB")
                        .header("X-Device-Id", "notification-preference-it-device")
                        .header("User-Agent", "Koala Notification Preference IT")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body("MARKETINGS", "EMAIL", true)))
                .andExpect(status().isOk());

        commercialMessageConsentDao.flush();
        Map<String, Object> task = jdbcTemplate.queryForMap("""
                select status, channel, granted, recipient, provider_source
                from iys_consent_tasks
                where user_id = ?
                """, subscriber.getId());

        assertEquals("PENDING", task.get("status"));
        assertEquals("EMAIL", task.get("channel"));
        assertEquals(true, task.get("granted"));
        assertEquals(subscriber.getEmail(), task.get("recipient"));
        assertEquals("HS_WEB", task.get("provider_source"));
    }

    @Test
    void aLocalCommercialOptInCannotBypassMissingIysCredentials() throws Exception {
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

        assertNull(notificationService.sendEmail(newsletter, DispatchPriority.NORMAL, true));
        verify(notificationProducerService, never()).sendEmailTask(any());
    }

    @Test
    void aCommercialSmsCannotBypassMissingIysCredentials() throws Exception {
        User subscriber = saveUser("commercial-sms@koala.test");
        subscriber.setPhoneNumber("+905551112233");
        subscriber.setPhoneVerified(true);
        userDao.saveAndFlush(subscriber);
        String token = login("commercial-sms@koala.test");
        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("MARKETINGS", "SMS", true)))
                .andExpect(status().isOk());

        SendSmsDto sms = new SendSmsDto();
        sms.setRecipient(subscriber);
        sms.setDestinationPhoneNumber(subscriber.getPhoneNumber());
        sms.setCategory(NotificationCategory.MARKETINGS);
        sms.setMessageBody("Yeni fırsatlar");

        assertNull(notificationService.sendSms(sms, DispatchPriority.NORMAL, true));
        verify(notificationProducerService, never()).sendSmsTask(any());
    }

    @Test
    void aCommercialSmsOptInRequiresAVerifiedPhone() throws Exception {
        User subscriber = saveUser("commercial-sms-no-phone@koala.test");
        String token = login(subscriber.getEmail());

        mockMvc.perform(put("/users/me/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("MARKETINGS", "SMS", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IYS_VERIFIED_RECIPIENT_REQUIRED"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from iys_consent_tasks where user_id = ?", Integer.class, subscriber.getId()));
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
        return login(email, null);
    }

    private String login(String email, String platform) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "notification-preference-it-device")
                        .header("User-Agent", "Koala Notification Preference IT")
                        .header("X-Client-Platform", platform == null ? "" : platform)
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

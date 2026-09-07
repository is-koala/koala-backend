package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.security.PasswordResetTokenGenerator;
import com.exskylab.koala.dataAccess.PasswordResetTokenDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.PasswordResetToken;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String FIRST_TOKEN = "U9l3Tj9X61jWhsa83UmRY2YkylyVObchM2t69yAlU9E";
    private static final String SECOND_TOKEN = "ZSngR1DGwejs7_YkFeZaDJmDlVX1Ut6B0dLpKDUVB5o";
    private static final String COMMIT_TOKEN = "Kj80JkZGYWeLAmGaK0rcSKyH4rj5R88k5hLjI9FHtEo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordResetTokenDao passwordResetTokenDao;

    @MockitoBean
    private PasswordResetTokenGenerator tokenGenerator;

    @MockitoBean
    private NotificationProducerService notificationProducerService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aPasswordResetLinkCanOnlyBeUsedOnce() throws Exception {
        String email = "password-reset-once@koala.test";
        saveUser(email);
        when(tokenGenerator.generate()).thenReturn(FIRST_TOKEN);

        requestResetLink(email);
        String body = resetBody(FIRST_TOKEN);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void requestingANewLinkInvalidatesThePreviousLink() throws Exception {
        String email = "password-reset-latest@koala.test";
        saveUser(email);
        when(tokenGenerator.generate()).thenReturn(FIRST_TOKEN, SECOND_TOKEN);

        requestResetLink(email);
        requestResetLink(email);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(FIRST_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_PASSWORD_RESET_TOKEN_INVALID"));

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(SECOND_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void anExpiredLinkCannotResetThePassword() throws Exception {
        String email = "password-reset-expired@koala.test";
        User user = saveUser(email);
        when(tokenGenerator.generate()).thenReturn(FIRST_TOKEN);
        requestResetLink(email);

        PasswordResetToken storedToken = passwordResetTokenDao.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        storedToken.setExpiresAt(Instant.now().minusSeconds(1));
        passwordResetTokenDao.saveAndFlush(storedToken);

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(FIRST_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void changingThePasswordWhileLoggedInInvalidatesAnExistingResetLink() throws Exception {
        String email = "password-reset-changed@koala.test";
        saveUser(email);
        when(tokenGenerator.generate()).thenReturn(FIRST_TOKEN);
        String accessToken = login(email, "EskiParola1");
        requestResetLink(email);

        mockMvc.perform(put("/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Device-Id", "password-reset-test-device")
                        .header("User-Agent", "Koala Password Reset IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "EskiParola1",
                                  "newPassword": "BaskaParola1",
                                  "confirmNewPassword": "BaskaParola1"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(FIRST_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    void successfulResetQueuesTheSecurityNoticeOnlyAfterCommit() throws Exception {
        String email = "password-reset-notice@koala.test";
        saveUser(email);
        when(tokenGenerator.generate()).thenReturn(COMMIT_TOKEN);

        requestResetLink(email);
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(COMMIT_TOKEN)))
                .andExpect(status().isOk());

        verify(notificationProducerService, times(1)).sendEmailTask(any());

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var task = org.mockito.ArgumentCaptor.forClass(EmailTaskDto.class);
        verify(notificationProducerService, times(2)).sendEmailTask(task.capture());
        assertEquals("password-reset-request-template", task.getAllValues().get(0).templateName());
        assertEquals("http://localhost:3000/sifre-sifirla?token=" + COMMIT_TOKEN,
                task.getAllValues().get(0).templateParameters().get("resetLink"));
        assertEquals("password-reset-completed-template", task.getAllValues().get(1).templateName());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yılmaz");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("EskiParola1"));
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        return userDao.saveAndFlush(user);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .header("X-Device-Id", "password-reset-test-device")
                        .header("User-Agent", "Koala Password Reset IT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data")
                .get("token")
                .asText();
    }

    private void requestResetLink(String email) throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
    }

    private String resetBody(String token) {
        return "{\"resetToken\":\"" + token + "\",\"newPassword\":\"YeniParola12\"}";
    }
}

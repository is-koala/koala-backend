package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class VerificationInfoIT extends AbstractIntegrationTest {

    private static final Pattern RAW_KEY = Pattern.compile("\"\\{[a-z][a-zA-Z0-9._-]*}\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationProducerService notificationProducerService;

    @Test
    void thePendingVerificationCarriesTheMomentTheCodeStopsWorking() throws Exception {
        Instant beforeRequest = Instant.now();

        MvcResult result = changeEmail("tr");

        String expiresAt = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("pendingVerifications").get(0).get("expiresAt").asString();

        Instant expiry = Instant.parse(expiresAt);
        assertTrue(expiry.isAfter(beforeRequest),
                "expiresAt gecmiste: " + expiry);
        assertTrue(expiry.isBefore(beforeRequest.plus(Duration.ofMinutes(16))),
                "expiresAt kodun on bes dakikalik omrunden uzak: " + expiry);
    }

    @Test
    void theClientCanBuildItsCountdownWithoutGuessingWhenTheScreenOpened() throws Exception {
        changeEmailExpecting("tr")
                .andExpect(jsonPath("$.data.pendingVerifications[0].expiresAt").exists())
                .andExpect(jsonPath("$.data.pendingVerifications[0].id").exists())
                .andExpect(jsonPath("$.data.pendingVerifications[0].type").value("EMAIL"));
    }

    @Test
    void noRawMessageKeyReachesTheClient() throws Exception {
        for (String language : new String[]{"tr", "en"}) {
            String body = changeEmail(language).getResponse().getContentAsString(StandardCharsets.UTF_8);

            Matcher rawKey = RAW_KEY.matcher(body);
            String leaked = rawKey.find() ? rawKey.group() : "";
            assertTrue(leaked.isEmpty(),
                    "Yanit ham i18n anahtari tasiyor (" + language + "): " + leaked + " | " + body);
        }
    }

    @Test
    void theSuccessEnvelopeFollowsTheAcceptLanguageHeader() throws Exception {
        changeEmailExpecting("tr").andExpect(jsonPath("$.message").value("Bilgileriniz güncellendi."));
        changeEmailExpecting("en").andExpect(jsonPath("$.message").value("Your details have been updated."));
    }

    @Test
    void theVerificationInfoNoLongerCarriesAMessageOfItsOwn() throws Exception {
        changeEmailExpecting("tr")
                .andExpect(jsonPath("$.data.pendingVerifications[0].message").doesNotExist());
    }

    private MvcResult changeEmail(String language) throws Exception {
        return changeEmailExpecting(language).andReturn();
    }

    private ResultActions changeEmailExpecting(String language) throws Exception {
        String owner = "verification-info-" + UUID.randomUUID() + "@koala.test";
        saveUser(owner);

        return mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + login(owner))
                        .header("Accept-Language", language)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"yeni-" + UUID.randomUUID() + "@koala.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingVerifications").isArray());
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
                        .header("X-Device-Id", "verification-info-it-device")
                        .header("User-Agent", "Koala Verification Info IT")
                        .header("X-Client-Platform", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"GecerliParola1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("token").asString();
    }
}

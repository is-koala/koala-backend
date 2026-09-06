package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.JwtService;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.dataAccess.SessionDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.Session;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class WebDeviceIdentityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void webLoginWithoutADeviceIdIsRejectedBeforeCredentialLookup() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("X-Client-Platform", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AUTH_WEB_DEVICE_ID_REQUIRED"));
    }

    @Test
    void webLoginWithAMalformedDeviceIdIsRejected() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("X-Client-Platform", "web")
                        .header("X-Device-Id", "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_WEB_DEVICE_ID_REQUIRED"));
    }

    @Test
    void webRefreshWithoutOrWithAMalformedDeviceIdIsRejected() throws Exception {
        User tokenUser = user();
        tokenUser.setId(java.util.UUID.randomUUID());
        String accessToken = jwtService.generateAccessToken(tokenUser, java.util.UUID.randomUUID());

        mockMvc.perform(post("/auth/refresh-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Client-Platform", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-used\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_WEB_DEVICE_ID_REQUIRED"));

        mockMvc.perform(post("/auth/refresh-token")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Client-Platform", "web")
                        .header("X-Device-Id", "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-used\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_WEB_DEVICE_ID_REQUIRED"));
    }

    @Test
    void changingTheWebDeviceIdDeactivatesTheSessionAndReturnsUnauthorized() throws Exception {
        User user = userDao.saveAndFlush(user());

        Device device = new Device();
        device.setUser(user);
        device.setType(DeviceType.WEB_BROWSER);
        device.setName("Browser A");
        device.setClientDeviceId("browser-profile-a");
        device.setFirstSeenAt(Instant.now());
        device.setLastSeenAt(Instant.now());
        device = deviceDao.saveAndFlush(device);

        Session session = new Session();
        session.setDevice(device);
        session.setRefreshTokenHash("not-used");
        session.setRefreshExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        session.setIpAddress("127.0.0.1");
        session.setLastAccessedAt(Instant.now());
        session.setActive(true);
        session = sessionDao.saveAndFlush(session);

        String accessToken = jwtService.generateAccessToken(user, session.getId());

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Client-Platform", "web")
                        .header("X-Device-Id", "browser-profile-b"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_SESSION_NOT_MATCHING_CURRENT_DEVICE"));

        assertFalse(sessionDao.findById(session.getId()).orElseThrow().isActive());
    }

    @Test
    void twoWebProfileIdsKeepTwoIndependentSessionsActive() throws Exception {
        User user = savedLoginUser();

        login(user, "browser-profile-a");
        login(user, "browser-profile-b");

        assertEquals(2, sessionDao.findAllByDevice_User_IdAndIsActive(user.getId(), true).size());
    }

    @Test
    void aSecondLoginForTheSameWebProfileReplacesItsPreviousSession() throws Exception {
        User user = savedLoginUser();

        login(user, "browser-profile-a");
        login(user, "browser-profile-a");

        assertEquals(1, sessionDao.findAllByDevice_User_IdAndIsActive(user.getId(), true).size());
        assertEquals(2, sessionDao.findAll().stream()
                .filter(session -> session.getDevice().getUser().getId().equals(user.getId()))
                .count());
    }

    private void login(User user, String deviceId) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .header("X-Client-Platform", "web")
                        .header("X-Device-Id", deviceId)
                        .header("User-Agent", "Koala browser test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"ValidPass1\"}"))
                .andExpect(status().isOk());
    }

    private User savedLoginUser() {
        User user = user();
        user.setPassword(passwordEncoder.encode("ValidPass1"));
        return userDao.saveAndFlush(user);
    }

    private User user() {
        User user = new User();
        user.setFirstName("Web");
        user.setLastName("Session");
        user.setEmail("web-session-" + java.util.UUID.randomUUID() + "@koala.test");
        user.setPassword("not-used");
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        return user;
    }
}

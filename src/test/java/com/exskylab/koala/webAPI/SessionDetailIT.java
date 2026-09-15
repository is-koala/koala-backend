package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.geoip.GeoIpResolver;
import com.exskylab.koala.core.helpers.DeviceHints;
import com.exskylab.koala.core.helpers.DeviceNaming;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.RunbookSql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SessionDetailIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "GecerliParola1";
    private static final String KNOWN_IP = "203.0.113.7";
    private static final String UNKNOWN_IP = "198.51.100.9";
    private static final String MOBILE_UA = "Koala (iOS)";
    private static final Path RUNBOOK = Path.of("docs/runbooks/sessions.md");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GeoIpResolver geoIpResolver;

    private record Client(String deviceId, String userAgent, String platform, DeviceHints hints, String ip) {
        static Client phone(String deviceId, DeviceHints hints) {
            return new Client(deviceId, MOBILE_UA, "ios", hints, KNOWN_IP);
        }

        static Client bareCurl(String userAgent) {
            return new Client(null, userAgent, null, DeviceHints.NONE, UNKNOWN_IP);
        }
    }

    private record Login(String token, UUID sessionId) {
    }

    @Test
    void theDeviceHeadersNameTheDeviceAfterModelOsAndAppVersion() throws Exception {
        String email = newUser("headers");
        Client phone = Client.phone("headers-device-000001", DeviceHints.of("iPhone15,3", "iOS 18.1", "1.1.0"));

        Login login = login(email, PASSWORD, phone);

        mockMvc.perform(with(get("/users/me/sessions"), phone, login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].deviceName").value("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0"))
                .andExpect(jsonPath("$.data[0].deviceType").value("IOS"))
                .andExpect(jsonPath("$.data[0].current").value(true));
    }

    @Test
    void withoutHeadersTheMobileUserAgentNeverShowsQuestionMarks() throws Exception {
        String email = newUser("bare-ua");
        Client phone = Client.phone("bare-ua-device-000001", DeviceHints.NONE);

        Login login = login(email, PASSWORD, phone);

        MvcResult result = mockMvc.perform(with(get("/users/me/sessions"), phone, login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName", not(containsString("??"))))
                .andReturn();
        String name = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get(0).get("deviceName").asText();
        assertFalse(name.isBlank(), "cihaz adı boş olmamalı");
    }

    @Test
    void anUnreadableUserAgentBecomesTheUnknownDeviceLabel() throws Exception {
        String email = newUser("hacker");
        Client curl = Client.bareCurl("hacker");

        Login login = login(email, PASSWORD, curl);

        mockMvc.perform(with(get("/users/me/sessions"), curl, login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName").value(DeviceNaming.UNKNOWN_DEVICE));
    }

    @Test
    void theDetailCarriesTheLocationAndTheDevicesLoginHistory() throws Exception {
        when(geoIpResolver.locate(KNOWN_IP)).thenReturn(Optional.of("Istanbul, TR"));
        String email = newUser("detail");
        Client phone = Client.phone("detail-device-00000001", DeviceHints.of("iPhone15,3", "iOS 18.1", "1.1.0"));

        Login first = login(email, PASSWORD, phone);
        attemptLogin(email, "yanlis-parola", phone).andExpect(status().isUnauthorized());
        Login second = login(email, PASSWORD, phone);

        mockMvc.perform(with(get("/users/me/sessions/{id}", second.sessionId()), phone, second.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(second.sessionId().toString()))
                .andExpect(jsonPath("$.data.deviceName").value("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0"))
                .andExpect(jsonPath("$.data.deviceCustomName").value(nullValue()))
                .andExpect(jsonPath("$.data.deviceType").value("IOS"))
                .andExpect(jsonPath("$.data.ipAddress").value(KNOWN_IP))
                .andExpect(jsonPath("$.data.location").value("Istanbul, TR"))
                .andExpect(jsonPath("$.data.current").value(true))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.lastAccessedAt").exists())
                .andExpect(jsonPath("$.data.refreshExpiresAt").exists())
                .andExpect(jsonPath("$.data.logins").value(2))
                .andExpect(jsonPath("$.data.loginHistory", hasSize(3)))
                .andExpect(jsonPath("$.data.loginHistory[0].success").value(true))
                .andExpect(jsonPath("$.data.loginHistory[1].success").value(false))
                .andExpect(jsonPath("$.data.loginHistory[2].success").value(true))
                .andExpect(jsonPath("$.data.loginHistory[1].ipAddress").value(KNOWN_IP))
                .andExpect(jsonPath("$.data.loginHistory[1].location").value("Istanbul, TR"))
                .andExpect(jsonPath("$.data.loginHistory[1].at").exists());

        assertNotEquals(first.sessionId(), second.sessionId());
    }

    @Test
    void withoutAGeoIpAnswerTheLocationIsNullAndNothingElseBreaks() throws Exception {
        String email = newUser("nogeo");
        Client curl = Client.bareCurl("curl/8.4.0");

        Login login = login(email, PASSWORD, curl);

        mockMvc.perform(with(get("/users/me/sessions/{id}", login.sessionId()), curl, login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ipAddress").value(UNKNOWN_IP))
                .andExpect(jsonPath("$.data.location").value(nullValue()))
                .andExpect(jsonPath("$.data.loginHistory[0].location").value(nullValue()))
                .andExpect(jsonPath("$.data.logins").value(1));
    }

    @Test
    void aClientWithoutADeviceIdIsMatchedByItsUserAgentEvenWhenTheAccountDoesNotExist() throws Exception {
        String email = newUser("ua-only");
        Client curl = Client.bareCurl("curl/8.4.0");
        Login login = login(email, PASSWORD, curl);

        attemptLogin(email, "yanlis-parola", curl).andExpect(status().isUnauthorized());
        String nobody = "sd-nobody-" + UUID.randomUUID().toString().substring(0, 8) + "@koala.test";
        attemptLogin(nobody, "yanlis-parola", curl).andExpect(status().isUnauthorized());
        Login again = login(email, PASSWORD, curl);
        settle();

        assertEquals(again.sessionId().toString(), jdbc.queryForObject(
                "select id from sessions where device_id = (select device_id from sessions where id = ?) "
                        + "and is_active = true", String.class, login.sessionId()),
                "X-Device-Id olmayan istemci aynı cihaz satırına dönmeli");
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from devices where user_id = (select id from users where email = ?) "
                        + "and is_deleted = false", Integer.class, email).intValue());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from login_attempts where user_id is null and device_id is null "
                        + "and email_attempted = ?", Integer.class, nobody).intValue(),
                "hesabı olmayan adresin denemesi cihaza bağlanmamalı");

        mockMvc.perform(with(get("/users/me/sessions/{id}", again.sessionId()), curl, again.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logins").value(2))
                .andExpect(jsonPath("$.data.loginHistory", hasSize(3)))
                .andExpect(jsonPath("$.data.loginHistory[1].success").value(false));
    }

    @Test
    void someoneElsesSessionLooksExactlyLikeAMissingOne() throws Exception {
        String mine = newUser("mine");
        String theirs = newUser("theirs");
        Client myPhone = Client.phone("mine-device-000000001", DeviceHints.NONE);
        Client theirPhone = Client.phone("their-device-00000001", DeviceHints.NONE);
        Login me = login(mine, PASSWORD, myPhone);
        Login them = login(theirs, PASSWORD, theirPhone);
        UUID phantom = UUID.randomUUID();

        String forTheirs = envelope(mockMvc.perform(
                with(get("/users/me/sessions/{id}", them.sessionId()), myPhone, me.token()))
                .andExpect(status().isNotFound()), them.sessionId());
        String forPhantom = envelope(mockMvc.perform(
                with(get("/users/me/sessions/{id}", phantom), myPhone, me.token()))
                .andExpect(status().isNotFound()), phantom);

        assertEquals(forPhantom, forTheirs,
                "Başkasının oturumunun yanıtı ile var olmayan oturumun yanıtı ayırt edilebiliyor");
    }

    @Test
    void aClosedSessionHasNoDetailEither() throws Exception {
        String email = newUser("closed");
        Client phone = Client.phone("closed-device-00000001", DeviceHints.NONE);
        Login stale = login(email, PASSWORD, phone);
        Login fresh = login(email, PASSWORD, phone);

        mockMvc.perform(with(get("/users/me/sessions/{id}", stale.sessionId()), phone, fresh.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MY_SESSION_NOT_FOUND"));
    }

    @Test
    void theDetailDoesNotGrowWithTheLoginHistory() throws Exception {
        String email = newUser("nplus1");
        Client phone = Client.phone("nplus1-device-00000001", DeviceHints.of("iPhone15,3", "iOS 18.1", "1.1.0"));
        Login single = login(email, PASSWORD, phone);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            settle();
            statistics.clear();
            mockMvc.perform(with(get("/users/me/sessions/{id}", single.sessionId()), phone, single.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginHistory", hasSize(1)));
            long statementsForOne = statistics.getPrepareStatementCount();

            attemptLogin(email, "yanlis-parola", phone).andExpect(status().isUnauthorized());
            login(email, PASSWORD, phone);
            Login latest = login(email, PASSWORD, phone);

            settle();
            statistics.clear();
            mockMvc.perform(with(get("/users/me/sessions/{id}", latest.sessionId()), phone, latest.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginHistory", hasSize(4)))
                    .andExpect(jsonPath("$.data.logins").value(3));

            assertEquals(statementsForOne, statistics.getPrepareStatementCount(),
                    "oturum detayı giriş geçmişiyle büyüyen sorgu atıyor (N+1)");
            assertEquals(0, statistics.getEntityFetchCount(), "oturum detayı satır başına lazy entity yüklüyor");

            settle();
            statistics.clear();
            mockMvc.perform(with(get("/users/me/sessions"), phone, latest.token()))
                    .andExpect(status().isOk());
            assertEquals(0, statistics.getEntityFetchCount(), "oturum listesi satır başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void renamingADeviceShowsUpInTheListAndTheDetailButNotForStrangers() throws Exception {
        String email = newUser("rename");
        String stranger = newUser("stranger");
        Client phone = Client.phone("rename-device-00000001", DeviceHints.of("iPhone15,3", "iOS 18.1", "1.1.0"));
        Client strangerPhone = Client.phone("stranger-device-0000001", DeviceHints.NONE);
        Login login = login(email, PASSWORD, phone);
        Login strangerLogin = login(stranger, PASSWORD, strangerPhone);
        UUID deviceId = deviceOf(login, phone);

        mockMvc.perform(with(patch("/users/me/devices/{id}", deviceId), phone, login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Evdeki iPad \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deviceId.toString()))
                .andExpect(jsonPath("$.data.name").value("Evdeki iPad"))
                .andExpect(jsonPath("$.data.customName").value("Evdeki iPad"));

        mockMvc.perform(with(get("/users/me/sessions"), phone, login.token()))
                .andExpect(jsonPath("$.data[0].deviceName").value("Evdeki iPad"));
        mockMvc.perform(with(get("/users/me/sessions/{id}", login.sessionId()), phone, login.token()))
                .andExpect(jsonPath("$.data.deviceName").value("Evdeki iPad"))
                .andExpect(jsonPath("$.data.deviceCustomName").value("Evdeki iPad"));

        mockMvc.perform(with(patch("/users/me/devices/{id}", deviceId), strangerPhone, strangerLogin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Benim\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_NOT_FOUND"));

        mockMvc.perform(with(patch("/users/me/devices/{id}", deviceId), phone, login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(61) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
        mockMvc.perform(with(patch("/users/me/devices/{id}", deviceId), phone, login.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertEquals("Evdeki iPad", jdbc.queryForObject(
                "select custom_name from devices where id = ?", String.class, deviceId));

        Login again = login(email, PASSWORD, phone);
        mockMvc.perform(with(get("/users/me/sessions"), phone, again.token()))
                .andExpect(jsonPath("$.data[0].deviceName").value("Evdeki iPad"));
    }

    @Test
    void theRunbookClosesOneSessionWithoutTouchingTheDevice() throws Exception {
        String email = newUser("runbook-session");
        Client phone = Client.phone("runbook-s-device-000001", DeviceHints.NONE);
        Login login = login(email, PASSWORD, phone);
        UUID userId = userDao.findByEmail(email).orElseThrow().getId();
        settle();

        int closed = jdbc.update(runbookCloseSession(), login.sessionId(), userId);
        settle();

        assertEquals(1, closed);
        mockMvc.perform(with(get("/users/me/sessions"), phone, login.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "select is_deleted from devices where id = ?", Boolean.class, deviceIdBySql(userId)));
        assertEquals(0, jdbc.update(runbookCloseSession(), login.sessionId(), UUID.randomUUID()),
                "yanlış kullanıcıyla oturum kapatılamamalı");
    }

    @Test
    void theRunbookClosesADeviceWithAllOfItsSessions() throws Exception {
        String email = newUser("runbook-device");
        Client phone = Client.phone("runbook-d-device-000001", DeviceHints.NONE);
        Client laptop = Client.bareCurl("curl/8.4.0");
        Login phoneLogin = login(email, PASSWORD, phone);
        Login laptopLogin = login(email, PASSWORD, laptop);
        UUID userId = userDao.findByEmail(email).orElseThrow().getId();
        UUID phoneDevice = deviceOf(phoneLogin, phone);
        settle();
        jdbc.update("update devices set push_token = 'fcm-token-1' where id = ?", phoneDevice);

        String[] statements = runbookCloseDevice();
        int sessionsClosed = jdbc.update(statements[0], phoneDevice, userId);
        int devicesClosed = jdbc.update(statements[1], phoneDevice, userId);
        settle();

        assertEquals(1, sessionsClosed);
        assertEquals(1, devicesClosed);
        mockMvc.perform(with(get("/users/me/sessions"), phone, phoneLogin.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
        mockMvc.perform(with(get("/users/me/sessions"), laptop, laptopLogin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(laptopLogin.sessionId().toString()));
        Map<String, Object> device = jdbc.queryForMap(
                "select is_deleted, push_token from devices where id = ?", phoneDevice);
        assertEquals(Boolean.TRUE, device.get("is_deleted"));
        assertNull(device.get("push_token"));
        assertEquals(1, jdbc.queryForObject(runbookVerify(), Integer.class, userId).intValue(),
                "yalnız dizüstü oturumu açık kalmalı");

        Login fresh = login(email, PASSWORD, phone);
        mockMvc.perform(with(get("/users/me/sessions"), phone, fresh.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    private static String runbookCloseSession() {
        String block = RunbookSql.theOnlyBlock(RUNBOOK,
                candidate -> candidate.startsWith("update sessions") && candidate.contains("where id = :session_id"),
                "tek oturumu kapatan update'i");
        assertTrue(block.contains("is_active = false"), block);
        assertTrue(block.contains("refresh_token_hash = null"), block);
        return block.replace(":session_id", "?").replace(":user_id", "?").replace(";", "");
    }

    private static String[] runbookCloseDevice() {
        String block = RunbookSql.theOnlyBlock(RUNBOOK,
                candidate -> candidate.startsWith("update sessions") && candidate.contains("update devices"),
                "cihazı bütün oturumlarıyla kapatan bloğu");
        String[] statements = block.split(";");
        assertEquals(2, statements.length, "blok iki update taşımalı: " + block);
        assertTrue(statements[1].contains("push_token = null"), block);
        return new String[] {
                statements[0].replace(":device_id", "?").replace(":user_id", "?"),
                statements[1].replace(":device_id", "?").replace(":user_id", "?")};
    }

    private static String runbookVerify() {
        String block = RunbookSql.theOnlyBlock(RUNBOOK,
                candidate -> candidate.startsWith("select count(*) as still_open"), "doğrulama sorgusunu");
        return block.replace(":user_id", "?").replace(";", "");
    }

    private String newUser(String label) {
        String email = "sd-" + label + "-" + UUID.randomUUID().toString().substring(0, 8) + "@koala.test";
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEmailVerified(true);
        user.setAuthorities(Set.of(Role.ROLE_USER));
        userDao.saveAndFlush(user);
        return email;
    }

    private ResultActions attemptLogin(String email, String password, Client client) throws Exception {
        return mockMvc.perform(with(post("/auth/login"), client, null)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private Login login(String email, String password, Client client) throws Exception {
        MvcResult result = attemptLogin(email, password, client)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
        String token = data.get("token").asText();
        UUID sessionId = UUID.fromString(objectMapper.readTree(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1])).get("jti").asText());
        return new Login(token, sessionId);
    }

    private UUID deviceOf(Login login, Client client) throws Exception {
        MvcResult result = mockMvc.perform(with(get("/users/me/sessions/{id}", login.sessionId()), client,
                login.token())).andExpect(status().isOk()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("deviceId").asText());
    }

    private void settle() {
        entityManager.flush();
        entityManager.clear();
    }

    private UUID deviceIdBySql(UUID userId) {
        return jdbc.queryForObject("select id from devices where user_id = ? and is_deleted = false", UUID.class,
                userId);
    }

    private static MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder builder, Client client,
                                                      String token) {
        builder.with(request -> {
            request.setRemoteAddr(client.ip());
            return request;
        });
        if (client.userAgent() != null) {
            builder.header("User-Agent", client.userAgent());
        }
        if (client.deviceId() != null) {
            builder.header("X-Device-Id", client.deviceId());
        }
        if (client.platform() != null) {
            builder.header("X-Client-Platform", client.platform());
        }
        if (client.hints().model() != null) {
            builder.header(DeviceHints.MODEL_HEADER, client.hints().model());
        }
        if (client.hints().os() != null) {
            builder.header(DeviceHints.OS_HEADER, client.hints().os());
        }
        if (client.hints().appVersion() != null) {
            builder.header(DeviceHints.APP_VERSION_HEADER, client.hints().appVersion());
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private String envelope(ResultActions actions, UUID resourceId) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed =
                new TreeMap<>(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString().replace(resourceId.toString(), "{istenen-id}");
    }
}
